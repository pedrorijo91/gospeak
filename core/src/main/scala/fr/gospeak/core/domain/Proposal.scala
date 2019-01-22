package fr.gospeak.core.domain

import fr.gospeak.core.domain.utils.{DataClass, Info, UuidIdBuilder}

final case class Proposal(id: Proposal.Id,
                          talk: Talk.Id,
                          group: Group.Id,
                          title: Talk.Title,
                          description: String,
                          info: Info)

object Proposal {

  final class Id private(value: String) extends DataClass(value)

  object Id extends UuidIdBuilder[Proposal.Id]("Proposal.Id", new Proposal.Id(_))

}
