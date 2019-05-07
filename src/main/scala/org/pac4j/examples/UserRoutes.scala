package org.pac4j.examples

import akka.actor.{ ActorRef, ActorSystem }
import akka.event.Logging

import scala.concurrent.duration._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity, StatusCodes }
import akka.http.scaladsl.server.{ Route, StandardRoute }
import akka.http.scaladsl.server.directives.MethodDirectives.delete
import akka.http.scaladsl.server.directives.MethodDirectives.get
import akka.http.scaladsl.server.directives.MethodDirectives.post
import akka.http.scaladsl.server.directives.RouteDirectives.complete
import akka.http.scaladsl.server.directives.PathDirectives.path

import scala.concurrent.{ ExecutionContext, Future }
import akka.pattern.ask
import akka.util.Timeout
import com.stackstate.pac4j.{ AkkaHttpSecurity, AuthenticatedRequest }
import com.stackstate.pac4j.AkkaHttpSecurity._
import org.pac4j.core.config.Config
import org.pac4j.http.client.indirect.FormClient

import scala.util.Try

object SimplePages {

  def loginForm(callbackUrl: String): StandardRoute = {
    val html = s"""
                 |<html>
                 |<body>
                 |<form action="$callbackUrl" method="POST">
                 |              <table>
                 |                <tr>
                 |                  <td><label for="username">User: </label></td><td><input id="username" type="text" name="username" value="" ></td>
                 |                </tr>
                 |                <tr>
                 |                  <td><label for="password">Password: </label></td><td><input id="password" type="password" name="password" value="" ></td>
                 |                </tr>
                 |              </table>
                 |
                 |              <br/>
                 |              <input type="submit" name="submit" value="Login" />
                 |</form>
                 |
                 |<br>
                 |You can login with these credentials:<br>
                 |user: einstein<br>
                 |password: password
                 |
                 |</body>
                 |</html>
               """.stripMargin
    complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, html))
  }

  def index(): StandardRoute = {
    val html = s"""
                  |<html>
                  |<body>
                  |  <a href="/users">protected page</a><br>
                  |
                  |  <a href="/auth/login">login with LDAP</a><br>
                  |
                  |  <a href="/auth/logout">logout</a><br>
                  |
                  |</form>
                  |</body>
                  |</html>
               """.stripMargin
    complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, html))
  }

}

//#user-routes-class
trait UserRoutes extends JsonSupport {
  //#user-routes-class

  // we leave these abstract, since they will be provided by the App
  implicit def system: ActorSystem

  def security: AkkaHttpSecurity

  def securityConfig: Config

  lazy val ldapCallbackUrl = Try(securityConfig.getClients.findClient("FormClient").asInstanceOf[FormClient].getCallbackUrl).getOrElse("")

  //#all-routes
  //#users-get-post
  //#users-get-delete
  lazy val userRoutes: Route =
    concat(
      path("") {
        SimplePages.index()
      },
      pathPrefix("auth") {
        concat(
          path("callback") {
            security.callback(defaultUrl = "/")
          },
          path("logout") {
            security.logout(defaultUrl = "/")
          },
          path("login") {
            get {
              SimplePages.loginForm(callbackUrl = ldapCallbackUrl)
            }
          })
      },
      path("users") {
        // security.withAuthentication(clients = "FormClient") { authReq: AuthenticatedRequest =>
        security.withAllClientsAuthentication() { authReq: AuthenticatedRequest =>
          get {
            complete(Users(authReq.profiles.map(p => User(name = p.getId))))
          }
        }
      })
  //#all-routes
}

case class User(name: String)
case class Users(users: Seq[User])