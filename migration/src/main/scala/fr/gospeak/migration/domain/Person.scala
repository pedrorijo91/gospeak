package fr.gospeak.migration.domain

import java.time.Instant

import fr.gospeak.core.domain.User
import fr.gospeak.core.domain.User.Profile
import fr.gospeak.libs.scalautils.Extensions._
import fr.gospeak.libs.scalautils.StringUtils
import fr.gospeak.libs.scalautils.domain.{EmailAddress, Url}
import fr.gospeak.migration.domain.utils.Meta
import fr.gospeak.migration.utils.AvatarUtils

import scala.util.Try

case class Person(id: String, // Person.Id,
                  data: PersonData,
                  auth: Option[PersonAuth],
                  meta: Meta) {
  def toUser: User = {
    val names = data.name.split(' ').filter(_.nonEmpty)
    val emailStr = auth.map(_.loginInfo).filter(_.providerID == "credentials").map(_.providerKey)
      .orElse(data.email)
      .getOrElse(StringUtils.slugify(data.name) + "@missing-email.com")
    val email = EmailAddress.from(emailStr).get
    val validated = auth.filter(_.activated).map(_ => Instant.ofEpochMilli(meta.created))
    val avatar = AvatarUtils.buildAvatarQuick(data.avatar, email)
    val profile: Profile = Profile(
      description = data.description,
      company = data.company,
      location = data.location,
      twitter = data.twitter.map(t => Url.from("https://twitter.com/" + t).get),
      linkedin = data.linkedin.map(Url.from(_).get),
      phone = data.phone,
      website = data.webSite.map(Url.from(_).get))

    Try(User(
      id = User.Id.from(id).get,
      slug = User.Slug.from(StringUtils.slugify(data.name)).get,
      firstName = names.headOption.getOrElse("N/A"),
      lastName = Option(names.drop(1).mkString(" ")).filter(_.nonEmpty).getOrElse("N/A"),
      email = email,
      emailValidated = validated,
      avatar = avatar,
      published = None,
      profile = profile,
      created = Instant.ofEpochMilli(meta.created),
      updated = Instant.ofEpochMilli(meta.updated))).mapFailure(e => new Exception(s"toUser error for $this", e)).get
  }
}

object Person {

  object Role {
    val user = "User"
    val organizer = "Organizer"
    val admin = "Admin"
  }

}

case class PersonData(name: String,
                      company: Option[String],
                      location: Option[String],
                      twitter: Option[String],
                      linkedin: Option[String],
                      email: Option[String],
                      phone: Option[String],
                      webSite: Option[String],
                      avatar: Option[String],
                      description: Option[String])

case class PersonAuth(loginInfo: LoginInfo,
                      role: String, // Person.Role.Value, (User, Organizer, Admin)
                      activated: Boolean) {
  def isOrga: Boolean = role == Person.Role.organizer || role == Person.Role.admin
}

case class LoginInfo(providerID: String, providerKey: String)
