package ru.solarev.apiDoc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.http.client.fluent.Content;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class CrptApi {
    private final int requestLimit;
    private final long requestLimitInterval;

    private final Semaphore semaphore;
    private final ScheduledExecutorService scheduler;

    private static final String BASE_URL = "https://ismp.crpt.ru/api/v3";
    private static final String CREATE = "/lk/documents/create";

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.requestLimitInterval = timeUnit.toMillis(1);
        this.requestLimit = requestLimit;

        semaphore = new Semaphore(requestLimit);
        scheduler = new ScheduledThreadPoolExecutor(1);
        scheduler.scheduleAtFixedRate(this::releasePermit, 0, requestLimitInterval, TimeUnit.MILLISECONDS);
    }

    private void releasePermit() {
        semaphore.release(requestLimit - semaphore.availablePermits());
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    /**
     * Создание документа для ввода в оборот товара, произведенного в РФ
     * @param document - Документ
     * @param signature - Подпись
     */
    public void createDocument(Document document, String signature) {
        try {
            semaphore.acquire();
            String documentJson = encodeBase64(convert(document));
            System.out.println(documentJson);
            BodyRequest bodyRequest = new BodyRequest(DocumentFormat.MANUAL, documentJson, ProductGroup.WITHOUT_GROUP,
                    signature, Type.LP_INTRODUCE_GOODS);
            sendPostRequest(BASE_URL.concat(CREATE), convert(bodyRequest), ContentType.APPLICATION_JSON);
        }catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private String sendPostRequest(String url, String bodyString, ContentType type) {
        Content postRequest = null;
        try {
            postRequest = Request.Post(url)
                            .bodyString(bodyString, type)
                            .execute().returnContent();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return postRequest != null ? postRequest.asString() : "";
    }

    private String encodeBase64(String data) {
        return new String(Base64.getEncoder().encode(data.getBytes()));
    }

    private String convert(Object body) {
        ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
        String json = null;
        try {
            json = ow.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return json;
    }

    @Data
    public class Document {
        private Description description;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private List<Product> products;
        private String reg_date;
        private String reg_number;
    }

    @Data
    public class Description {
        private String participantInn;
    }

    @Data
    public class Product {
        private String certificate_document;
        private String certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private String production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;
    }

    @Data
    @AllArgsConstructor
    class BodyRequest {
        private DocumentFormat document_format;
        private String product_document;
        private ProductGroup product_group;
        private String signature;
        private Type type;
    }

    enum DocumentFormat {
        MANUAL, XML, CSV
    }

    enum Type {
        LP_INTRODUCE_GOODS, LP_INTRODUCE_GOODS_XML, LP_INTRODUCE_GOODS_CSV
    }

    enum ProductGroup {
        CLOTHES(1), SHOES(2), TOBACCO(3), PERFUMERY(4), TIRES(5),
        ELECTRONICS(6), PHARMA(7), MILK(8), BICYCLE(9), WHEELCHAIRS(10), WITHOUT_GROUP(11);

        private int code;

        ProductGroup(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }
}
