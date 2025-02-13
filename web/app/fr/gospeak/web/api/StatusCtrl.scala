package fr.gospeak.web.api

import java.time.Instant

import fr.gospeak.web.AppConf
import fr.gospeak.web.api.StatusCtrl._
import fr.gospeak.web.utils.ApiCtrl
import generated.BuildInfo
import play.api.libs.json._
import play.api.mvc.{Action, AnyContent, ControllerComponents, Request}

import scala.util.Try

class StatusCtrl(cc: ControllerComponents,
                 conf: AppConf) extends ApiCtrl(cc) {
  private val startedAt = Instant.now()

  def getStatus: Action[AnyContent] = Action { implicit req: Request[AnyContent] =>
    Ok(Json.toJson(AppStatus(startedAt, generated.BuildInfo, conf))(appStatusWrites))
  }

}

object StatusCtrl {
  implicit val gitStatusWrites: Writes[GitStatus] = Json.writes[GitStatus]
  implicit val srvStatusWrites: Writes[SrvStatus] = Json.writes[SrvStatus]
  implicit val appStatusWrites: Writes[AppStatus] = Json.writes[AppStatus]

  final case class SrvStatus(db: String,
                             email: String)

  final case class GitStatus(subject: String,
                             branch: String,
                             hash: String,
                             commiter: String,
                             date: Option[Instant])

  final case class AppStatus(env: String,
                             buildAt: Instant,
                             startedAt: Instant,
                             buildVersion: Int,
                             appVersion: String,
                             scalaVersion: String,
                             sbtVersion: String,
                             services: SrvStatus,
                             lastCommit: GitStatus)

  object AppStatus {
    def apply(startedAt: Instant, info: BuildInfo.type, conf: AppConf): AppStatus =
      new AppStatus(
        env = conf.application.env.toString,
        buildAt = Instant.ofEpochMilli(info.builtAtMillis),
        startedAt = startedAt,
        buildVersion = info.buildInfoBuildNumber,
        appVersion = info.version,
        scalaVersion = info.scalaVersion,
        sbtVersion = info.sbtVersion,
        services = SrvStatus(
          db = conf.database.getClass.getSimpleName,
          email = conf.emailService.getClass.getName.split("\\$").filter(_.nonEmpty).last),
        lastCommit = GitStatus(
          subject = info.gitSubject,
          branch = info.gitBranch,
          hash = info.gitHash,
          commiter = info.gitCommitterName,
          date = Try(Instant.ofEpochSecond(info.gitCommitterDate.toLong)).toOption))
  }

}
