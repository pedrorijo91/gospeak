package fr.gospeak.web.utils

import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.concurrent.TimeUnit

import cats.implicits._
import fr.gospeak.core.domain._
import fr.gospeak.core.services.meetup.domain.{MeetupEvent, MeetupGroup, MeetupVenue}
import fr.gospeak.core.services.slack.domain.{SlackAction, SlackToken}
import fr.gospeak.infra.libs.timeshape.TimeShape
import fr.gospeak.libs.scalautils.Crypto.AesSecretKey
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.domain.MustacheTmpl.MustacheMarkdownTmpl
import fr.gospeak.libs.scalautils.domain._
import fr.gospeak.web.utils.Extensions._
import fr.gospeak.web.utils.Mappings.Utils._
import play.api.data.Forms._
import play.api.data.format.Formats._
import play.api.data.format.Formatter
import play.api.data.validation.{Constraint, Constraints, ValidationError, Invalid => PlayInvalid, Valid => PlayValid}
import play.api.data.{FormError, Mapping, WrappedMapping}

import scala.concurrent.duration._
import scala.util.Try

object Mappings {
  val requiredConstraint = "constraint.required"
  val requiredError = "error.required"
  val patternConstraint = "constraint.pattern"
  val numberError = "error.number"
  val datetimeError = "error.datetime"
  val formatError = "error.format"
  val formatConstraint = "constraint.format"
  val passwordConstraint = "constraint.password"
  val passwordError = "error.password"

  val localDateFormat = "dd/MM/yyyy"
  val localDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern(localDateFormat)

  val double: Mapping[Double] = of(doubleFormat)
  val instant: Mapping[Instant] = stringEitherMapping(s => Try(LocalDateTime.parse(s).toInstant(ZoneOffset.UTC)).toEither, _.atZone(ZoneOffset.UTC).toLocalDateTime.toString, datetimeError) // FIXME manage timezone
  val myLocalDateTime: Mapping[LocalDateTime] = mapping(
    "date" -> localDate("dd/MM/yyyy"),
    "time" -> localTime("HH:mm")
  )({ case (d, t) => LocalDateTime.of(d, t) })(dt => Some(dt.toLocalDate -> dt.toLocalTime))
  val chronoUnit: Mapping[ChronoUnit] = stringEitherMapping(d => Try(ChronoUnit.valueOf(d)).toEither, _.name(), formatError)
  val periodUnit: Mapping[TimePeriod.PeriodUnit] = stringEitherMapping(d => TimePeriod.PeriodUnit.all.find(_.toString == d).toEither, _.toString, formatError)
  val period: Mapping[TimePeriod] = mapping(
    "length" -> longNumber,
    "unit" -> periodUnit
  )(TimePeriod.apply)(TimePeriod.unapply)
  val timeUnit: Mapping[TimeUnit] = stringEitherMapping(d => Try(TimeUnit.valueOf(d)).toEither, _.name(), formatError)
  val duration: Mapping[FiniteDuration] = mapping(
    "length" -> longNumber,
    "unit" -> timeUnit
  )(new FiniteDuration(_, _))(d => Some(d.length -> d.unit))
  val emailAddress: Mapping[EmailAddress] = WrappedMapping(text.verifying(Constraints.emailAddress(), Constraints.maxLength(Values.maxLength.title)), (s: String) => EmailAddress.from(s).get, _.value)
  val url: Mapping[Url] = stringEitherMapping(Url.from, _.value, formatError, Constraints.maxLength(Values.maxLength.title))
  val slides: Mapping[Slides] = stringEitherMapping(Slides.from, _.value, formatError, Constraints.maxLength(Values.maxLength.title))
  val video: Mapping[Video] = stringEitherMapping(Video.from, _.value, formatError, Constraints.maxLength(Values.maxLength.title))
  val secret: Mapping[Secret] = textMapping(Secret, _.decode)
  val password: Mapping[Secret] = secret.verifying(Constraint[Secret](passwordConstraint) { o =>
    if (o.decode.length >= 8) PlayValid
    else PlayInvalid(ValidationError(passwordError))
  })
  val markdown: Mapping[Markdown] = textMapping(Markdown, _.value, Constraints.maxLength(Values.maxLength.text))
  val currency: Mapping[Price.Currency] = stringEitherMapping(Price.Currency.from(_).toEither, _.value, formatError)
  val price: Mapping[Price] = mapping(
    "amount" -> double,
    "currency" -> currency
  )(Price.apply)(Price.unapply)

  def gMapPlace(timeShape: TimeShape): Mapping[GMapPlace] = of(new Formatter[GMapPlace] {
    private def buildGMapPlace(key: String)(id: String,
                                            name: String,
                                            streetNo: Option[String],
                                            street: Option[String],
                                            postalCode: Option[String],
                                            locality: Option[String],
                                            country: String,
                                            formatted: String,
                                            input: String,
                                            lat: Double,
                                            lng: Double,
                                            url: String,
                                            website: Option[String],
                                            phone: Option[String],
                                            utcOffset: Int): Either[List[FormError], GMapPlace] = {
      timeShape.getZoneId(Geo(lat, lng)).map { timezone =>
        GMapPlace(id, name, streetNo, street, postalCode, locality, country, formatted, input, Geo(lat, lng), url, website, phone, utcOffset, timezone)
      }.toEither(List(FormError(s"$key.timezone", s"Unable to get timezone for Geo($lat, $lng) :(")))
    }

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], GMapPlace] = (
      data.eitherGet(s"$key.id").toValidatedNec,
      data.eitherGet(s"$key.name").toValidatedNec,
      data.get(s"$key.streetNo").validNec[FormError],
      data.get(s"$key.street").validNec[FormError],
      data.get(s"$key.postalCode").validNec[FormError],
      data.get(s"$key.locality").validNec[FormError],
      data.eitherGet(s"$key.country").toValidatedNec,
      data.eitherGet(s"$key.formatted").toValidatedNec,
      data.eitherGet(s"$key.input").toValidatedNec,
      data.eitherGetAndParse(s"$key.lat", _.tryDouble, numberError).toValidatedNec,
      data.eitherGetAndParse(s"$key.lng", _.tryDouble, numberError).toValidatedNec,
      data.eitherGet(s"$key.url").toValidatedNec,
      data.get(s"$key.website").validNec[FormError],
      data.get(s"$key.phone").validNec[FormError],
      data.eitherGetAndParse(s"$key.utcOffset", _.tryInt, numberError).toValidatedNec
      ).mapN(buildGMapPlace(key)).toEither.left.map(_.toList).flatten

    override def unbind(key: String, value: GMapPlace): Map[String, String] =
      Seq(
        s"$key.id" -> Some(value.id),
        s"$key.name" -> Some(value.name),
        s"$key.streetNo" -> value.streetNo,
        s"$key.street" -> value.street,
        s"$key.postalCode" -> value.postalCode,
        s"$key.locality" -> value.locality,
        s"$key.country" -> Some(value.country),
        s"$key.formatted" -> Some(value.formatted),
        s"$key.input" -> Some(value.input),
        s"$key.lat" -> Some(value.geo.lat.toString),
        s"$key.lng" -> Some(value.geo.lng.toString),
        s"$key.url" -> Some(value.url),
        s"$key.website" -> value.website,
        s"$key.phone" -> value.phone,
        s"$key.utcOffset" -> Some(value.utcOffset.toString)
      ).collect { case (k, Some(v)) => (k, v) }.toMap
  })

  private val tag: Mapping[Tag] = WrappedMapping[String, Tag](text(1, Tag.maxSize), s => Tag(s.trim), _.value)
  val tags: Mapping[Seq[Tag]] = seq(tag).verifying(s"Can't add more than ${Tag.maxNumber} tags", _.length <= Tag.maxNumber)

  val userSlug: Mapping[User.Slug] = slugMapping(User.Slug)
  val userProfileStatus: Mapping[User.Profile.Status] = stringEitherMapping(User.Profile.Status.from, _.value, formatError)
  val groupSlug: Mapping[Group.Slug] = slugMapping(Group.Slug)
  val groupName: Mapping[Group.Name] = nonEmptyTextMapping(Group.Name, _.value, Constraints.maxLength(Values.maxLength.title))
  val eventSlug: Mapping[Event.Slug] = slugMapping(Event.Slug)
  val eventName: Mapping[Event.Name] = nonEmptyTextMapping(Event.Name, _.value, Constraints.maxLength(Values.maxLength.title))
  val cfpId: Mapping[Cfp.Id] = idMapping(Cfp.Id)
  val cfpSlug: Mapping[Cfp.Slug] = slugMapping(Cfp.Slug)
  val cfpName: Mapping[Cfp.Name] = nonEmptyTextMapping(Cfp.Name, _.value, Constraints.maxLength(Values.maxLength.title))
  val talkSlug: Mapping[Talk.Slug] = slugMapping(Talk.Slug)
  val talkTitle: Mapping[Talk.Title] = nonEmptyTextMapping(Talk.Title, _.value, Constraints.maxLength(Values.maxLength.title))
  val partnerId: Mapping[Partner.Id] = idMapping(Partner.Id)
  val partnerSlug: Mapping[Partner.Slug] = slugMapping(Partner.Slug)
  val partnerName: Mapping[Partner.Name] = nonEmptyTextMapping(Partner.Name, _.value, Constraints.maxLength(Values.maxLength.title))
  val contactId: Mapping[Contact.Id] = idMapping(Contact.Id)
  val venueId: Mapping[Venue.Id] = idMapping(Venue.Id)
  val sponsorPackId: Mapping[SponsorPack.Id] = idMapping(SponsorPack.Id)
  val sponsorPackSlug: Mapping[SponsorPack.Slug] = slugMapping(SponsorPack.Slug)
  val sponsorPackName: Mapping[SponsorPack.Name] = nonEmptyTextMapping(SponsorPack.Name, _.value, Constraints.maxLength(Values.maxLength.title))
  val contactFirstName: Mapping[Contact.FirstName] = nonEmptyTextMapping(Contact.FirstName, _.value)
  val contactLastName: Mapping[Contact.LastName] = nonEmptyTextMapping(Contact.LastName, _.value)
  val meetupGroupSlug: Mapping[MeetupGroup.Slug] = stringEitherMapping(MeetupGroup.Slug.from, _.value, formatError, Constraints.nonEmpty)
  val meetupEventId: Mapping[MeetupEvent.Id] = stringEitherMapping(MeetupEvent.Id.from, _.value.toString, formatError, Constraints.nonEmpty)
  val meetupVenueId: Mapping[MeetupVenue.Id] = stringEitherMapping(MeetupVenue.Id.from, _.value.toString, formatError, Constraints.nonEmpty)
  val eventRefs: Mapping[Event.ExtRefs] = mapping(
    "meetup" -> optional(mapping(
      "group" -> meetupGroupSlug,
      "event" -> meetupEventId
    )(MeetupEvent.Ref.apply)(MeetupEvent.Ref.unapply))
  )(Event.ExtRefs.apply)(Event.ExtRefs.unapply)
  val venueRefs: Mapping[Venue.ExtRefs] = mapping(
    "meetup" -> optional(mapping(
      "group" -> meetupGroupSlug,
      "venue" -> meetupVenueId
    )(MeetupVenue.Ref.apply)(MeetupVenue.Ref.unapply))
  )(Venue.ExtRefs.apply)(Venue.ExtRefs.unapply)

  def slackToken(key: AesSecretKey): Mapping[SlackToken] = stringEitherMapping(SlackToken.from(_, key).toEither, _.decode(key).get, formatError, Constraints.nonEmpty)

  private def templateFormatter[A]: Formatter[MustacheMarkdownTmpl[A]] = new Formatter[MustacheMarkdownTmpl[A]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], MustacheMarkdownTmpl[A]] =
      data.eitherGet(s"$key.kind").left.map(Seq(_)).flatMap {
        case "Mustache" => data.get(s"$key.value").map(v => Right(MustacheMarkdownTmpl[A](v))).getOrElse(Left(Seq(FormError(s"$key.value", s"Missing key '$key.value'"))))
        case v => Left(Seq(FormError(s"$key.kind", s"Invalid value '$v' for key '$key.kind'")))
      }

    override def unbind(key: String, value: MustacheMarkdownTmpl[A]): Map[String, String] = value match {
      case MustacheMarkdownTmpl(v) => Map(s"$key.kind" -> "Mustache", s"$key.value" -> v)
    }
  }

  def template[A]: Mapping[MustacheMarkdownTmpl[A]] = of(templateFormatter)

  val groupSettingsEvent: Mapping[Group.Settings.Action.Trigger] = of(new Formatter[Group.Settings.Action.Trigger] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Group.Settings.Action.Trigger] =
      data.eitherGetAndParse(key, v => Group.Settings.Action.Trigger.from(v).toTry(CustomException(v)), formatError).left.map(Seq(_))

    override def unbind(key: String, value: Group.Settings.Action.Trigger): Map[String, String] = Map(key -> value.toString)
  })

  val groupSettingsAction: Mapping[Group.Settings.Action] = of(new Formatter[Group.Settings.Action] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Group.Settings.Action] = {
      data.eitherGet(s"$key.kind").left.map(Seq(_)).flatMap {
        case "Slack.PostMessage" => (
          templateFormatter.bind(s"$key.channel", data),
          templateFormatter.bind(s"$key.message", data),
          implicitly[Formatter[Boolean]].bind(s"$key.createdChannelIfNotExist", data),
          implicitly[Formatter[Boolean]].bind(s"$key.inviteEverybody", data)
          ).mapN(SlackAction.PostMessage.apply).map(Group.Settings.Action.Slack)
        case v => Left(Seq(FormError(s"$key.kind", s"action kind '$v' not found")))
      }
    }

    override def unbind(key: String, value: Group.Settings.Action): Map[String, String] = value match {
      case Group.Settings.Action.Slack(p: SlackAction.PostMessage) =>
        Map(s"$key.kind" -> "Slack.PostMessage") ++
          templateFormatter.unbind(s"$key.channel", p.channel) ++
          templateFormatter.unbind(s"$key.message", p.message) ++
          implicitly[Formatter[Boolean]].unbind(s"$key.createdChannelIfNotExist", p.createdChannelIfNotExist) ++
          implicitly[Formatter[Boolean]].unbind(s"$key.inviteEverybody", p.inviteEverybody)
    }
  })

  private[utils] object Utils {
    def textMapping[A](from: String => A, to: A => String, constraints: Constraint[String]*): Mapping[A] =
      WrappedMapping(text.verifying(constraints: _*), from, to)

    def nonEmptyTextMapping[A](from: String => A, to: A => String, constraints: Constraint[String]*): Mapping[A] =
      WrappedMapping(nonEmptyText.verifying(constraints: _*), from, to)

    def stringEitherMapping[A, E](from: String => Either[E, A], to: A => String, errorMessage: String, constraints: Constraint[String]*): Mapping[A] =
      WrappedMapping(text.verifying(constraints: _*).verifying(format(from, errorMessage)), (s: String) => from(s).get, to)

    def idMapping[A <: IId](builder: UuidIdBuilder[A]): Mapping[A] =
      WrappedMapping(text.verifying(Constraints.nonEmpty()), (s: String) => builder.from(s).get, _.value)

    def slugMapping[A <: ISlug](builder: SlugBuilder[A]): Mapping[A] =
      WrappedMapping(text.verifying(Constraints.nonEmpty(), Constraints.pattern(SlugBuilder.pattern), Constraints.maxLength(SlugBuilder.maxLength)), (s: String) => builder.from(s).get, _.value)

    private def format[E, A](parse: String => Either[E, A], errorMessage: String = formatError): Constraint[String] =
      Constraint[String](formatConstraint) { o =>
        if (o == null) PlayInvalid(ValidationError(errorMessage))
        else if (parse(o.trim).isLeft) PlayInvalid(ValidationError(errorMessage))
        else PlayValid
      }
  }

}
