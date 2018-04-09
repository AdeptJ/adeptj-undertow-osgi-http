/*
###############################################################################
#                                                                             # 
#    Copyright 2016, AdeptJ (http://www.adeptj.com)                           #
#                                                                             #
#    Licensed under the Apache License, Version 2.0 (the "License");          #
#    you may not use this file except in compliance with the License.         #
#    You may obtain a copy of the License at                                  #
#                                                                             #
#        http://www.apache.org/licenses/LICENSE-2.0                           #
#                                                                             #
#    Unless required by applicable law or agreed to in writing, software      #
#    distributed under the License is distributed on an "AS IS" BASIS,        #
#    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. #
#    See the License for the specific language governing permissions and      #
#    limitations under the License.                                           #
#                                                                             #
###############################################################################
*/

package io.adeptj.runtime.servlet;

import io.adeptj.runtime.common.BridgeServletConfigHolder;
import io.adeptj.runtime.common.BundleContextHolder;
import io.adeptj.runtime.common.RequestUtil;
import io.adeptj.runtime.common.ResponseUtil;
import io.adeptj.runtime.common.Times;
import io.adeptj.runtime.osgi.ServiceTrackers;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

/**
 * BridgeServlet acts as a bridge between ServletContainer and embedded OSGi HttpService.
 * <p>
 * All the incoming requests for OSGi resources delegated to the Felix DispatcherServlet which further
 * delegates to Felix {@link org.apache.felix.http.base.internal.dispatch.Dispatcher} which maintains
 * a registry of managed HttpServlet/Filter(s) etc.
 * <p>
 * Depending upon the resolution by Felix Dispatcher the request is being further dispatched to actual HttpServlet/Filter.
 *
 *
 * <b>This HttpServlet listens at "/" i.e root.<b>
 *
 * @author Rakesh.Kumar, AdeptJ
 */
public class BridgeServlet extends HttpServlet {

    private static final long serialVersionUID = 702778293237417284L;

    private static final Logger LOGGER = LoggerFactory.getLogger(BridgeServlet.class);

    private static final String UNAVAILABLE_MSG = "Can't serve request: [{}], DispatcherServlet is unavailable!!";

    private static final String FELIX_DISPATCHER_EXCEPTION_MSG = "Exception set by Felix Dispatcher!!";

    /**
     * Open the DispatcherServletTracker.
     */
    @Override
    public void init() {
        long startTime = System.nanoTime();
        LOGGER.info("Initializing BridgeServlet!!");
        LOGGER.info("Opening DispatcherServletTracker which initializes the Felix DispatcherServlet!!");
        // Store BridgeServlet's ServletConfig which is used to init Felix DispatcherServlet.
        BridgeServletConfigHolder.INSTANCE.setBridgeServletConfig(this.getServletConfig());
        BundleContext bundleContext = BundleContextHolder.INSTANCE.getBundleContext();
        ServiceTrackers.INSTANCE.openDispatcherServletTracker(bundleContext);
        LOGGER.info("BridgeServlet initialized in [{}] ms!!", Times.elapsedMillis(startTime));
    }

    /**
     * Bridge for Felix DispatcherServlet, delegate all the calls to underlying DispatcherServlet.
     *
     * @see class header for detailed description.
     */
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) {
        HttpServlet dispatcherServlet = ServiceTrackers.INSTANCE.getDispatcherServlet();
        try {
            if (dispatcherServlet == null) {
                LOGGER.error(UNAVAILABLE_MSG, req.getRequestURI());
                ResponseUtil.unavailable(resp);
            } else {
                dispatcherServlet.service(req, resp);
                RequestUtil.logException(req, LOGGER, FELIX_DISPATCHER_EXCEPTION_MSG);
            }
        } catch (Exception ex) { // NOSONAR
            LOGGER.error("Exception while handling request: [{}]", req.getRequestURI(), ex);
            ResponseUtil.sendError(resp, SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Historically, this method used to close the DispatcherServletTracker but due to a change in Felix Http service
     * which caused NPE, this logic has been moved to FrameworkShutdownHandler.
     * <p>
     * GitHub issue for the same can be located here: <a href="https://github.com/AdeptJ/adeptj-runtime/issues/4">GitHub Issues</>
     * <p>
     * Note: This method still exists to convey the order in which shutdown sequence initiates.
     */
    @Override
    public void destroy() {
        LOGGER.info("Destroying BridgeServlet!!");
    }
}