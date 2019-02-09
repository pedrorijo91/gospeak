package fr.gospeak.core.domain

import java.time.Instant

import fr.gospeak.core.domain.utils.Info
import fr.gospeak.libs.scalautils.domain.{DataClass, Markdown, SlugBuilder, UuidIdBuilder}

final case class Event(id: Event.Id,
                       group: Group.Id,
                       slug: Event.Slug,
                       name: Event.Name,
                       start: Instant,
                       // duration: Option[Duration]
                       description: Option[Markdown],
                       venue: Option[String],
                       talks: Seq[Proposal.Id],
                       info: Info) {
  def data: Event.Data = Event.Data(slug, name, start)
}

object Event {

  final class Id private(value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[Id]("Event.Id", new Id(_))

  final class Slug private(value: String) extends DataClass(value)

  object Slug extends SlugBuilder[Slug]("Event.Slug", new Slug(_))

  final case class Name(value: String) extends AnyVal

  final case class Data(slug: Event.Slug, name: Event.Name, start: Instant)

}
