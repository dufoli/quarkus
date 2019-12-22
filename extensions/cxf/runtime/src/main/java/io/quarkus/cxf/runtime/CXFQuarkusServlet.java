package io.quarkus.cxf.runtime;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.transport.http_undertow.CxfUndertowServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.quarkus.arc.Arc;

public class CXFQuarkusServlet extends CxfUndertowServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(CXFQuarkusServlet.class);

    public static class WebServiceConfig {
        private String path;
        private String className;

        public WebServiceConfig(String path, String className) {
            super();
            this.path = path;
            this.className = className;
        }

        public String getClassName() {
            return className;
        }

        public String getPath() {
            return path;
        }

        @Override
        public String toString() {
            return "Web Service " + className + " on " + path;
        }

    }

    private static final long serialVersionUID = 1L;

    private static final List<WebServiceConfig> WEB_SERVICES = new ArrayList<>();

    @Override
    protected void invoke(HttpServletRequest request, HttpServletResponse response) throws ServletException {
        // You can also use the simple frontend API to do this
        ServerFactoryBean factory = new ServerFactoryBean();

        for (WebServiceConfig config : WEB_SERVICES) {
            Object instanceService = Arc.container().instance(config.getClassName()).get();
            if (instanceService != null) {
                factory.setServiceBean(instanceService);
                factory.setAddress(config.getPath());
                factory.create();
                LOGGER.info(config.toString() + " available.");
            } else {
                LOGGER.error("Cannot initialize " + config.toString());
            }
        }
        super.invoke(request, response);

    }

    public static void publish(String path, String webService) {
        WEB_SERVICES.add(new WebServiceConfig(path, webService));
    }
}
