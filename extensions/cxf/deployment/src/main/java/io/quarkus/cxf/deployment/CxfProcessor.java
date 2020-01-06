package io.quarkus.cxf.deployment;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;

import io.quarkus.arc.Unremovable;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanGizmoAdaptor;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.cxf.runtime.CXFQuarkusServlet;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import io.quarkus.deployment.util.HashUtil;
import io.quarkus.gizmo.ClassCreator;
import io.quarkus.gizmo.ClassOutput;
import io.quarkus.gizmo.MethodCreator;
import io.quarkus.gizmo.MethodDescriptor;
import io.quarkus.gizmo.ResultHandle;
import io.quarkus.undertow.deployment.ServletBuildItem;

/**
 * Processor that finds CXF web service classes in the deployment
 */
public class CxfProcessor {

    private static final String JAX_WS_CXF_SERVLET = "org.apache.cxf.transport.servlet.CXFNonSpringServlet;";

    private static final DotName WEBSERVICE_ANNOTATION = DotName.createSimple("javax.jws.WebService");
    private static final DotName ABSTRACT_FEATURE = DotName.createSimple("org.apache.cxf.feature.AbstractFeature");
    private static final DotName ABSTRACT_INTERCEPTOR = DotName.createSimple("org.apache.cxf.phase.AbstractPhaseInterceptor");

    /**
     * JAX-RS configuration.
     */
    CxfConfig cxfConfig;

    @BuildStep
    public void build(
            CombinedIndexBuildItem combinedIndexBuildItem,
            BuildProducer<FeatureBuildItem> feature,
            BuildProducer<ServletBuildItem> servlet,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeans) throws Exception {
        IndexView index = combinedIndexBuildItem.getIndex();

        for (AnnotationInstance annotation : index.getAnnotations(WEBSERVICE_ANNOTATION)) {
            if (annotation.target().kind() == AnnotationTarget.Kind.CLASS) {
                reflectiveClass
                        .produce(new ReflectiveClassBuildItem(true, true, annotation.target().asClass().name().toString()));
            }
        }

        feature.produce(new FeatureBuildItem(FeatureBuildItem.CXF));

        String mappingPath = getMappingPath(cxfConfig.path);
        servlet.produce(ServletBuildItem.builder(JAX_WS_CXF_SERVLET, CXFQuarkusServlet.class.getName())
                .setLoadOnStartup(1).addMapping(mappingPath).setAsyncSupported(true).build());
        reflectiveClass.produce(new ReflectiveClassBuildItem(false, false, CXFQuarkusServlet.class.getName()));

        for (Entry<String, CxfEndpointConfig> webServicesByPath : cxfConfig.endpoints.entrySet()) {
            CXFQuarkusServlet.WebServiceConfig wsConfig = CXFQuarkusServlet.publish(webServicesByPath.getKey(),
                    webServicesByPath.getValue().implementor);
            DotName webServiceImplementor = DotName.createSimple(webServicesByPath.getValue().implementor);
            for (AnnotationInstance annotation : index.getClassByName(webServiceImplementor).classAnnotations()) {
                switch (annotation.name().toString()) {
                    case "org.apache.cxf.feature.Features":
                        HashSet<String> features = new HashSet<String>(
                                Arrays.asList(annotation.value("features").asStringArray()));
                        wsConfig.getFeatures().addAll(features);
                        unremovableBeans.produce(new UnremovableBeanBuildItem(
                                new UnremovableBeanBuildItem.BeanClassNamesExclusion(features)));
                        reflectiveClass
                                .produce(
                                        new ReflectiveClassBuildItem(true, true, annotation.value("features").asStringArray()));
                        break;
                    case "org.apache.cxf.interceptor.InInterceptors":
                        HashSet<String> inInterceptors = new HashSet<String>(
                                Arrays.asList(annotation.value("interceptors").asStringArray()));
                        wsConfig.getInInterceptors().addAll(inInterceptors);
                        unremovableBeans.produce(new UnremovableBeanBuildItem(
                                new UnremovableBeanBuildItem.BeanClassNamesExclusion(inInterceptors)));
                        reflectiveClass
                                .produce(new ReflectiveClassBuildItem(true, true,
                                        annotation.value("interceptors").asStringArray()));
                        break;
                    case "org.apache.cxf.interceptor.OutInterceptors":
                        HashSet<String> outInterceptors = new HashSet<String>(
                                Arrays.asList(annotation.value("interceptors").asStringArray()));
                        wsConfig.getOutInterceptors().addAll(outInterceptors);
                        unremovableBeans.produce(new UnremovableBeanBuildItem(
                                new UnremovableBeanBuildItem.BeanClassNamesExclusion(outInterceptors)));
                        reflectiveClass
                                .produce(new ReflectiveClassBuildItem(true, true,
                                        annotation.value("interceptors").asStringArray()));
                        break;
                    case "org.apache.cxf.interceptor.OutFaultInterceptors":
                        HashSet<String> outFaultInterceptors = new HashSet<String>(
                                Arrays.asList(annotation.value("interceptors").asStringArray()));
                        wsConfig.getOutFaultInterceptors().addAll(outFaultInterceptors);
                        unremovableBeans.produce(new UnremovableBeanBuildItem(
                                new UnremovableBeanBuildItem.BeanClassNamesExclusion(outFaultInterceptors)));
                        reflectiveClass
                                .produce(new ReflectiveClassBuildItem(true, true,
                                        annotation.value("interceptors").asStringArray()));
                        break;
                    case "org.apache.cxf.interceptor.InFaultInterceptors":
                        HashSet<String> inFaultInterceptors = new HashSet<String>(
                                Arrays.asList(annotation.value("interceptors").asStringArray()));
                        wsConfig.getInFaultInterceptors().addAll(inFaultInterceptors);
                        unremovableBeans.produce(new UnremovableBeanBuildItem(
                                new UnremovableBeanBuildItem.BeanClassNamesExclusion(inFaultInterceptors)));
                        reflectiveClass
                                .produce(new ReflectiveClassBuildItem(true, true,
                                        annotation.value("interceptors").asStringArray()));
                        break;
                }
            }
        }

        for (ClassInfo subclass : index.getAllKnownSubclasses(ABSTRACT_FEATURE)) {
            reflectiveClass.produce(new ReflectiveClassBuildItem(true, true, subclass.name().toString()));
        }
        for (ClassInfo subclass : index.getAllKnownSubclasses(ABSTRACT_INTERCEPTOR)) {
            reflectiveClass.produce(new ReflectiveClassBuildItem(true, true, subclass.name().toString()));
        }
    }

    @BuildStep
    List<RuntimeInitializedClassBuildItem> runtimeInitializedClasses() {
        return Arrays.asList(
                new RuntimeInitializedClassBuildItem("io.netty.buffer.PooledByteBufAllocator"),
                new RuntimeInitializedClassBuildItem("io.netty.buffer.UnpooledHeapByteBuf"),
                new RuntimeInitializedClassBuildItem("io.netty.buffer.UnpooledUnsafeHeapByteBuf"),
                new RuntimeInitializedClassBuildItem(
                        "io.netty.buffer.UnpooledByteBufAllocator$InstrumentedUnpooledUnsafeHeapByteBuf"),
                new RuntimeInitializedClassBuildItem("io.netty.buffer.AbstractReferenceCountedByteBuf"));
    }

    @BuildStep
    public void registerReflectionItems(BuildProducer<ReflectiveClassBuildItem> reflectiveItems) {
        reflectiveItems.produce(new ReflectiveClassBuildItem(true, true,
                "com.sun.xml.fastinfoset.stax.StAXDocumentParser",
                "com.sun.xml.fastinfoset.stax.StAXDocumentSerializer",
                "com.sun.xml.fastinfoset.stax.factory.StAXEventFactory"));
    }

    @BuildStep
    NativeImageResourceBuildItem nativeImageResourceBuildItem() {
        return new NativeImageResourceBuildItem("com/sun/xml/fastinfoset/resources/ResourceBundle.properties",
                "META-INF/cxf/bus-extensions.txt",
                "META-INF/cxf/cxf.xml",
                "META-INF/cxf/org.apache.cxf.bus.factory",
                "META-INF/services/org.apache.cxf.bus.factory",
                "META-INF/blueprint.handlers",
                "META-INF/spring.handlers",
                "META-INF/spring.schemas",
                "OSGI-INF/metatype/workqueue.xml",
                "schemas/core.xsd",
                "schemas/blueprint/core.xsd",
                "schemas/wsdl/XMLSchema.xsd",
                "schemas/wsdl/addressing.xjb",
                "schemas/wsdl/addressing.xsd",
                "schemas/wsdl/addressing200403.xjb",
                "schemas/wsdl/addressing200403.xsd",
                "schemas/wsdl/http.xjb",
                "schemas/wsdl/http.xsd",
                "schemas/wsdl/mime-binding.xsd",
                "schemas/wsdl/soap-binding.xsd",
                "schemas/wsdl/soap-encoding.xsd",
                "schemas/wsdl/soap12-binding.xsd",
                "schemas/wsdl/swaref.xsd",
                "schemas/wsdl/ws-addr-wsdl.xjb",
                "schemas/wsdl/ws-addr-wsdl.xsd",
                "schemas/wsdl/ws-addr.xsd",
                "schemas/wsdl/wsdl.xjb",
                "schemas/wsdl/wsdl.xsd",
                "schemas/wsdl/wsrm.xsd",
                "schemas/wsdl/xmime.xsd",
                "schemas/wsdl/xml.xsd",
                "schemas/configuratio/cxf-beans.xsd",
                "schemas/configuration/extension.xsd",
                "schemas/configuration/parameterized-types.xsd",
                "schemas/configuration/security.xjb",
                "schemas/configuration/security.xsd");
    }

    private String getMappingPath(String path) {
        String mappingPath;
        if (path.endsWith("/")) {
            mappingPath = path + "*";
        } else {
            mappingPath = path + "/*";
        }
        return mappingPath;
    }

    @BuildStep
    public void createBeans(
            BuildProducer<UnremovableBeanBuildItem> unremovableBeans,
            BuildProducer<GeneratedBeanBuildItem> generatedBeans) throws Exception {
        for (Entry<String, CxfEndpointConfig> webServicesByPath : cxfConfig.endpoints.entrySet()) {
            ClassOutput classOutput = new GeneratedBeanGizmoAdaptor(generatedBeans);
            String webServiceName = webServicesByPath.getValue().implementor;
            String producerClassName = webServiceName + "Producer";
            ClassCreator classCreator = ClassCreator.builder().classOutput(classOutput)
                    .className(producerClassName)
                    .build();
            classCreator.addAnnotation(ApplicationScoped.class);

            unremovableBeans.produce(new UnremovableBeanBuildItem(
                    new UnremovableBeanBuildItem.BeanClassNameExclusion(producerClassName)));

            MethodCreator namedWebServiceMethodCreator = classCreator.getMethodCreator(
                    "createWebService_" + HashUtil.sha1(webServiceName),
                    webServiceName);
            namedWebServiceMethodCreator.addAnnotation(ApplicationScoped.class);
            namedWebServiceMethodCreator.addAnnotation(Unremovable.class);
            namedWebServiceMethodCreator.addAnnotation(Produces.class);
            namedWebServiceMethodCreator.addAnnotation(AnnotationInstance.create(DotNames.NAMED, null,
                    new AnnotationValue[] { AnnotationValue.createStringValue("value", webServiceName) }));

            ResultHandle namedWebService = namedWebServiceMethodCreator
                    .newInstance(MethodDescriptor.ofConstructor(webServiceName));

            namedWebServiceMethodCreator.returnValue(namedWebService);
            classCreator.close();
        }

    }

}
