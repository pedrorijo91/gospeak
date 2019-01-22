package fr.gospeak.core.domain

import cats.data.NonEmptyList
import fr.gospeak.core.domain.utils.{DataClass, Info, UuidIdBuilder}

final case class Talk(id: Talk.Id,
                      slug: Talk.Slug,
                      title: Talk.Title,
                      description: String,
                      speakers: NonEmptyList[User.Id],
                      info: Info)

object Talk {

  final class Id private(value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[Talk.Id]("Presentation.Id", new Talk.Id(_))

  final case class Slug(value: String) extends AnyVal

  final case class Title(value: String) extends AnyVal

}
