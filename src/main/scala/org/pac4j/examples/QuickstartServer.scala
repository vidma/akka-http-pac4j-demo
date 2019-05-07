package org.pac4j.examples

//#quick-start-server
import scala.concurrent.{ Await, ExecutionContext, Future }
import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.util.{ Failure, Success }
import akka.actor.{ ActorRef, ActorSystem }
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ ContentTypes, HttpEntity, HttpResponse }
import akka.http.scaladsl.server.RouteResult.Complete
import akka.http.scaladsl.server.{ Route, RouteResult }
import akka.stream.ActorMaterializer
import com.stackstate.pac4j.{ AkkaHttpSecurity, AkkaHttpWebContext }
import com.stackstate.pac4j.http.AkkaHttpSessionStore
import com.stackstate.pac4j.store.InMemorySessionStorage
import org.ldaptive.{ ConnectionConfig, ConnectionFactory }
import org.ldaptive.pool.PooledConnectionFactory
import org.pac4j.core.authorization.authorizer.RequireAnyRoleAuthorizer
import org.pac4j.core.client.Clients
import org.pac4j.core.config.Config
import org.pac4j.http.client.indirect.FormClient
import com.stackstate.pac4j.http.AkkaHttpActionAdapter
import org.pac4j.core.context.HttpConstants
import org.pac4j.core.http.adapter.HttpActionAdapter
import akka.http.scaladsl.model.StatusCodes._

//#main-class
object QuickstartServer extends App with UserRoutes {

  import scala.concurrent.duration

  // set up ActorSystem and other dependencies here
  //#main-class
  //#server-bootstrapping
  implicit val system: ActorSystem = ActorSystem("helloAkkaHttpServer")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = system.dispatcher
  //#server-bootstrapping

  lazy val securityConfig: Config = {
    val ldapSettings = LdapAuthSettingsData(
      server = "ldap://ldap.forumsys.com:389",
      usersDN = "cn=read-only-admin,dc=example,dc=com",
      basePeopleDn = "dc=example,dc=com",
      groupsAttribute = "objectClass")

    val callbackBaseUrl = "/auth"
    // val loginUrl = getBaseUrl(conf) + conf.get[String]("pac4j.login_url")
    val loginUrl = "/auth/login"

    // modify here to any other Auth method supported by pac4j
    val authClientNames = Seq("FormClient")

    val enabledClients = authClientNames.map {
      case "FormClient" =>
        val ldapAuthenticator = LdapAuthenticatorFactory.createLdapAuthenticator(ldapSettings)
        new FormClient(loginUrl, ldapAuthenticator)
    }

    val clients = new Clients(callbackBaseUrl + "/callback", enabledClients: _*)
    val config = new Config(clients)

    //val logoutUrl = conf.getOptional[String]("pac4j.logout_url").getOrElse("/logout")
    //val loginUrl =  conf.getOptional[String]("pac4j.login_url").getOrElse("/login")

    // make non-authorized not redirect!
    config.setHttpActionAdapter(new ForbiddenWithoutRedirectActionAdapter(loginUrl, logoutUrl = "/auth/logout"))
    config
  }

  import scala.concurrent.duration._
  val sessionStorage = new InMemorySessionStorage(sessionLifetime = 10.minutes) // FIXME: make configurable
  lazy val security: AkkaHttpSecurity = new AkkaHttpSecurity(securityConfig, sessionStorage)

  //#main-class
  // from the UserRoutes trait
  lazy val routes: Route = userRoutes
  //#main-class

  //#http-server
  val serverBinding: Future[Http.ServerBinding] = Http().bindAndHandle(routes, "localhost", 8080)

  serverBinding.onComplete {
    case Success(bound) =>
      println(s"Server online at http://${bound.localAddress.getHostString}:${bound.localAddress.getPort}/")
    case Failure(e) =>
      Console.err.println(s"Server could not start!")
      e.printStackTrace()
      system.terminate()
  }

  Await.result(system.whenTerminated, Duration.Inf)
  //#http-server
  //#main-class
}
//#main-class
//#quick-start-server

class ForbiddenWithoutRedirectActionAdapter(loginUrl: String, logoutUrl: String) extends HttpActionAdapter[Future[RouteResult], AkkaHttpWebContext] {
  override def adapt(code: Int, context: AkkaHttpWebContext): Future[Complete] = {
    code match {
      // Prevent FormClient to redirect to loginUrl when page is unaccessible
      // FIXME: this is a bit hacky, but works OK :)
      case HttpConstants.TEMP_REDIRECT if context.getChanges.headers.find(_.name == "Location").exists(_.value().contains("login")) =>
        // context.addResponseSessionCookie()
        val ct = ContentTypes.`text/html(UTF-8)`
        val entity = HttpEntity(contentType = ct, "Forbidden".getBytes("UTF-8"))
        Future.successful(Complete(HttpResponse(Forbidden, entity = entity)))

      case _ => AkkaHttpActionAdapter.adapt(code, context)
    }
  }
}

case class LdapAuthSettingsData(
  server: String, // e.g. "ldap://ldap.forumsys.com:389"
  usersDN: String, // e.g. "cn=read-only-admin,dc=example,dc=com"
  basePeopleDn: String, // e.g. "uid=%s,dc=example,dc=com"
  groupsAttribute: String // e.g. "objectClass"
) {
  def ldapAttributesToFetch: String = s"mail,uid,cn,$groupsAttribute"
}

object LdapAuthenticatorFactory {
  def createLdapAuthenticator(conf: LdapAuthSettingsData) = {
    val ldapServerUrl = conf.server
    val usersDN = conf.usersDN
    val basePeopleDn = conf.basePeopleDn // FIXME: rename to basePeopleDN, in dam-entities and dam-admin
    val ldapAttributes = conf.ldapAttributesToFetch + ",o"
    val roleLdapAttribute = conf.groupsAttribute

    import org.ldaptive.DefaultConnectionFactory
    import org.ldaptive.auth.FormatDnResolver
    import org.ldaptive.auth.PooledBindAuthenticationHandler
    import org.ldaptive.pool.BlockingConnectionPool
    import org.ldaptive.pool.IdlePruneStrategy
    import org.ldaptive.pool.PoolConfig
    import org.ldaptive.pool.SearchValidator
    import org.pac4j.ldap.profile.service.LdapProfileService
    import org.ldaptive.auth.SearchDnResolver

    // ldaptive:
    //val dnResolver = new FormatDnResolver
    //dnResolver.setFormat(basePeopleDnFormat)

    val connectionConfig = new ConnectionConfig()
    // TODO: connectionConfig.setConnectTimeout(500)
    // TODO: connectionConfig.setResponseTimeout(1000)
    connectionConfig.setLdapUrl(ldapServerUrl)
    val connectionFactory = new DefaultConnectionFactory
    connectionFactory.setConnectionConfig(connectionConfig)

    val dnResolver = new SearchDnResolver(connectionFactory)
    dnResolver.setBaseDn(basePeopleDn)
    dnResolver.setUserFilter("(uid={user})")

    val poolConfig = new PoolConfig
    poolConfig.setMinPoolSize(1)
    poolConfig.setMaxPoolSize(2)
    poolConfig.setValidateOnCheckOut(true)
    poolConfig.setValidateOnCheckIn(true)
    poolConfig.setValidatePeriodically(false)
    val searchValidator = new SearchValidator
    val pruneStrategy = new IdlePruneStrategy
    val connectionPool = new BlockingConnectionPool
    connectionPool.setPoolConfig(poolConfig)
    // TODO: connectionPool.setBlockWaitTime(1000)
    connectionPool.setValidator(searchValidator)
    connectionPool.setPruneStrategy(pruneStrategy)
    connectionPool.setConnectionFactory(connectionFactory)
    connectionPool.initialize()
    val pooledConnectionFactory = new PooledConnectionFactory()
    pooledConnectionFactory.setConnectionPool(connectionPool)
    val handler = new PooledBindAuthenticationHandler
    handler.setConnectionFactory(pooledConnectionFactory)
    val ldaptiveAuthenticator = new org.ldaptive.auth.Authenticator()
    ldaptiveAuthenticator.setDnResolver(dnResolver)
    ldaptiveAuthenticator.setAuthenticationHandler(handler)
    // val profileService = new RoleAwareGroupCheckingLdapProfileService(connectionFactory, ldaptiveAuthenticator, ldapAttributes, usersDN, dummyUserOps, conf)
    // profileService.setRoleLdapAttribute(roleLdapAttribute)
    val profileService = new LdapProfileService(connectionFactory, ldaptiveAuthenticator, ldapAttributes, usersDN)
    profileService
  }
}