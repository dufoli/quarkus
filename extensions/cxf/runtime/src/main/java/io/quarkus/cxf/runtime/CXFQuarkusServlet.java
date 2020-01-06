package io.quarkus.cxf.runtime;

import java.util.ArrayList;
import java.util.List;

import javax.servlet.ServletConfig;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.feature.Feature;
import org.apache.cxf.frontend.ServerFactoryBean;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.transport.servlet.CXFNonSpringServlet;
import org.jboss.logging.Logger;

import io.quarkus.arc.Arc;

public class CXFQuarkusServlet extends CXFNonSpringServlet {

    private static final Logger LOGGER = Logger.getLogger(CXFQuarkusServlet.class);

    private static final List<WebServiceConfig> WEB_SERVICES = new ArrayList<>();

    @Override
    public void loadBus(ServletConfig servletConfig) {
        super.loadBus(servletConfig);

        Bus bus = getBus();
        BusFactory.setDefaultBus(bus);

        ServerFactoryBean factory = new ServerFactoryBean();
        factory.setBus(bus);

        for (WebServiceConfig config : WEB_SERVICES) {
            Object instanceService = Arc.container().instance(config.getClassName()).get();
            if (instanceService != null) {
                factory.setServiceBean(instanceService);
                factory.setAddress(config.getPath());
                if (config.features.size() > 0) {
                    List<Feature> features = new ArrayList<>();
                    for (String feature : config.features) {
                        Feature instanceFeature = (Feature) Arc.container().instance(feature).get();
                        features.add(instanceFeature);
                    }
                    factory.setFeatures(features);
                }

                Server server = factory.create();
                for (String className : config.getInFaultInterceptors()) {
                    Interceptor interceptor = (Interceptor) Arc.container().instance(className).get();
                    server.getEndpoint().getInFaultInterceptors().add(interceptor);
                }
                for (String className : config.getInInterceptors()) {
                    Interceptor interceptor = (Interceptor) Arc.container().instance(className).get();
                    server.getEndpoint().getInInterceptors().add(interceptor);
                }
                for (String className : config.getOutFaultInterceptors()) {
                    Interceptor interceptor = (Interceptor) Arc.container().instance(className).get();
                    server.getEndpoint().getOutFaultInterceptors().add(interceptor);
                }
                for (String className : config.getOutInterceptors()) {
                    Interceptor interceptor = (Interceptor) Arc.container().instance(className).get();
                    server.getEndpoint().getOutInterceptors().add(interceptor);
                }

                LOGGER.info(config.toString() + " available.");
            } else {
                LOGGER.error("Cannot initialize " + config.toString());
            }
        }
    }

    public static WebServiceConfig publish(String path, String webService) {
        WebServiceConfig webServiceConfig = new WebServiceConfig(path, webService);
        WEB_SERVICES.add(webServiceConfig);
        return webServiceConfig;
    }

    public static class WebServiceConfig {
        private String path;
        private String className;
        private List<String> inInterceptors;
        private List<String> outInterceptors;
        private List<String> outFaultInterceptors;
        private List<String> inFaultInterceptors;
        private List<String> features;

        public WebServiceConfig(String path, String className) {
            super();
            this.path = path;
            this.className = className;
            this.inInterceptors = new ArrayList<>();
            this.outInterceptors = new ArrayList<>();
            this.outFaultInterceptors = new ArrayList<>();
            this.inFaultInterceptors = new ArrayList<>();
            this.features = new ArrayList<>();
        }

        public String getClassName() {
            return className;
        }

        public String getPath() {
            return path;
        }

        public List<String> getFeatures() {
            return features;
        }

        public List<String> getInInterceptors() {
            return inInterceptors;
        }

        public List<String> getOutInterceptors() {
            return outInterceptors;
        }

        public List<String> getOutFaultInterceptors() {
            return outFaultInterceptors;
        }

        public List<String> getInFaultInterceptors() {
            return inFaultInterceptors;
        }

        @Override
        public String toString() {
            return "Web Service " + className + " on " + path;
        }

    }
}
