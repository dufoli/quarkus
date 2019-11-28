package io.quarkus.cxf.deployment.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.function.Supplier;

import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import javax.xml.ws.soap.SOAPBinding;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkus.test.QuarkusDevModeTest;
import io.restassured.RestAssured;
import io.restassured.response.Response;

public class CxfServiceTest {

    @RegisterExtension
    public static final QuarkusDevModeTest test = new QuarkusDevModeTest()
            .setArchiveProducer(new Supplier<JavaArchive>() {
                @Override
                public JavaArchive get() {
                    return ShrinkWrap.create(JavaArchive.class)
                            .addClass(FruitWebServiceImpl.class)
                            .addClass(FruitWebService.class)
                            .addClass(Fruit.class)
                            .addClass(FruitImpl.class)
                            .addClass(FruitAdapter.class)
                            .addAsResource("application.properties");
                }
            });

    private static QName SERVICE_NAME = new QName("http://test.deployment.cxf.quarkus.io/", "FruitWebServiceImpl");
    private static QName PORT_NAME = new QName("http://test.deployment.cxf.quarkus.io/", "FruitWebServiceImplPortType");

    private Service service;
    private FruitWebService fruitProxy;
    private FruitWebServiceImpl fruitImpl;

    {
        service = Service.create(SERVICE_NAME);
        final String endpointAddress = "http://localhost:8080/fruit";
        service.addPort(PORT_NAME, SOAPBinding.SOAP11HTTP_BINDING, endpointAddress);
    }

    @BeforeEach
    public void reinstantiateBaeldungInstances() {
        fruitImpl = new FruitWebServiceImpl();
        fruitProxy = service.getPort(PORT_NAME, FruitWebService.class);
    }

    @Test
    public void whenUsingHelloMethod_thenCorrect() {
        String xml = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:tem=\"http://tempuri.org/\">\n"
                +
                "   <soapenv:Header/>\n" +
                "   <soapenv:Body>\n" +
                "      <tem:count>\n" +
                "      </tem:count>\n" +
                "   </soapenv:Body>\n" +
                "</soapenv:Envelope>";
        String xmlrsp = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body><ns1:countResponse xmlns:ns1=\"http://test.deployment.cxf.quarkus.io/\"><ns2:return xmlns:ns2=\"http://test.deployment.cxf.quarkus.io/\">2</ns2:return></ns1:countResponse></soap:Body></soap:Envelope>";

        Response response = RestAssured.given().header("Content-Type", "text/xml").and().body(xml).when().post("/fruit");
        response.then().statusCode(200);
        // quick hack here to test soap with rest assured
        // and quick hack to just jeck string instead of parsing xml
        // how we parse xml in java... must be something like xmlReader ????
        /*
         * final XMLInputFactory inputFactory = XMLInputFactory.newInstance();
         * final SAXBuilder builder = new SAXBuilder();
         * builder.build();
         * inputFactory.createXMLStreamReader(response.body().asInputStream());
         * while (reader.hasNext()) {
         * int eventType = reader.next();
         * switch (eventType) {
         * case XMLStreamReader.START_ELEMENT:
         * // handle start element
         * case XMLStreamReader.ATTRIBUTE:
         * // handle attribute
         * }
         */
        Assertions.assertEquals(xmlrsp, response.body().asString());
    }
}
