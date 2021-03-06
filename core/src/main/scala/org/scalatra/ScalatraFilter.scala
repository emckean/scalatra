package org.scalatra

import servlet.ServletBase
import scala.util.DynamicVariable
import javax.servlet.http.{ HttpServletResponse, HttpServletRequest }
import javax.servlet._

/**
 * An implementation of the Scalatra DSL in a filter.  You may prefer a filter
 * to a ScalatraServlet if:
 *
 * $ - you are sharing a URL space with another servlet or filter and want to
 *     delegate unmatched requests.  This is very useful when migrating
 *     legacy applications one page or resource at a time.
 *
 *
 * Unlike a ScalatraServlet, does not send 404 or 405 errors on non-matching
 * routes.  Instead, it delegates to the filter chain.
 *
 * If in doubt, extend ScalatraServlet instead.
 *
 * @see ScalatraServlet
 */
trait ScalatraFilter extends Filter with ServletBase {
  private[this] val _filterChain = new DynamicVariable[FilterChain](null)
  protected def filterChain = _filterChain.value

  def doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
    val httpRequest = request.asInstanceOf[HttpServletRequest]
    val httpResponse = response.asInstanceOf[HttpServletResponse]

    _filterChain.withValue(chain) {
      handle(httpRequest, httpResponse)
    }
  }

  // What goes in servletPath and what goes in pathInfo depends on how the underlying servlet is mapped.
  // Unlike the Scalatra servlet, we'll use both here by default.  Don't like it?  Override it.
  def requestPath = {
    def getRequestPath = request.getRequestURI match {
      case requestURI: String =>
        var uri = requestURI
        if (request.getContextPath.length > 0) uri = uri.substring(request.getContextPath.length)
        if (uri.length == 0) {
          uri = "/"
        } else {
          val pos = uri.indexOf(';')
          if (pos >= 0) uri = uri.substring(0, pos)
        }
        UriDecoder.firstStep(uri)
      case null => "/"
    }

    request.get("org.scalatra.ScalatraFilter.requestPath") match {
      case Some(uri) => uri.toString
      case _ => {
        val requestPath = getRequestPath
        request.setAttribute("org.scalatra.ScalatraFilter.requestPath", requestPath)
        requestPath.toString
      }
    }
  }

  protected def routeBasePath = {
    if (servletContext == null)
      throw new IllegalStateException("routeBasePath requires an initialized servlet context to determine the context path")
    servletContext.getContextPath
  }

  protected var doNotFound: Action = () => filterChain.doFilter(request, response)

  methodNotAllowed { _ => filterChain.doFilter(request, response) }

  type ConfigT = FilterConfig

  // see Initializable.initialize for why
  def init(filterConfig: FilterConfig) {
    initialize(filterConfig)
  }

  def destroy {}
}
