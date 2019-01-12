package fr.gospeak.web.user.talks

import fr.gospeak.web.Values
import fr.gospeak.web.user.UserCtrl
import fr.gospeak.web.user.talks.TalkCtrl._
import fr.gospeak.web.views.domain.{Breadcrumb, HeaderInfo, NavLink}
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TalkCtrl(cc: ControllerComponents) extends AbstractController(cc) {
  private val user = Values.user // logged user

  def list(): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    for {
      talks <- Values.getTalks(user.id)
    } yield Ok(views.html.list(talks)(UserCtrl.header.activeFor(routes.TalkCtrl.list()), listBreadcrumb(user.name)))
  }

  def detail(talk: String): Action[AnyContent] = Action.async { implicit req: Request[AnyContent] =>
    for {
      talkId <- Values.getTalkId(talk)
      talkOpt <- talkId.map(Values.getTalk(_, user.id)).getOrElse(Future.successful(None))
      proposals <- talkOpt.map(t => Values.getProposals(t.id)).getOrElse(Future.successful(Seq()))
    } yield talkOpt
      .map { talkElt => Ok(views.html.detail(talkElt, proposals)(header(talk), breadcrumb(user.name, talk -> talkElt.title.value))) }
      .getOrElse(NotFound)
  }
}

object TalkCtrl {
  def listBreadcrumb(user: String): Breadcrumb =
    UserCtrl.breadcrumb(user).add("Talks" -> routes.TalkCtrl.list())

  def header(talk: String): HeaderInfo =
    UserCtrl.header.copy(brand = NavLink("Gospeak", routes.TalkCtrl.detail(talk)))
      .copy(brand = NavLink("Gospeak", fr.gospeak.web.user.routes.UserCtrl.index()))
      .activeFor(routes.TalkCtrl.list())

  def breadcrumb(user: String, talk: (String, String)): Breadcrumb =
    listBreadcrumb(user).add(talk._2 -> routes.TalkCtrl.detail(talk._1))
}
