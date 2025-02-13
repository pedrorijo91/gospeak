package fr.gospeak.web.api.ui

import fr.gospeak.infra.services.EmbedSrv
import fr.gospeak.libs.scalautils.domain.{Markdown, Url}
import fr.gospeak.web.utils.{ApiCtrl, MarkdownUtils}
import play.api.mvc.{Action, AnyContent, ControllerComponents}

class UtilsCtrl(cc: ControllerComponents) extends ApiCtrl(cc) {
  def markdownToHtml(): Action[String] = Action(parse.text) { implicit req =>
    val md = Markdown(req.body)
    val html = MarkdownUtils.render(md)
    Ok(html.value)
  }

  def embed(url: Url): Action[AnyContent] = Action.async { implicit req =>
    EmbedSrv.embedCode(url)
      .map { code => Ok(code.value) }
      .unsafeToFuture()
  }
}
