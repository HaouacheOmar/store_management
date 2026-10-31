package com.store.sales;

import com.jayway.jsonpath.JsonPath;
import com.store.common.contract.StockContract.PricedLine;
import com.store.common.contract.StockContract.StockRequest;
import com.store.common.events.Events;
import com.store.common.security.TestTokens;
import com.store.common.testing.SharedContainers;
import com.store.sales.client.CatalogClient;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.security.jwt-secret=" + TestTokens.SECRET, "eureka.client.enabled=false"})
@AutoConfigureMockMvc
class SalesFlowTest extends SharedContainers {

    @MockitoBean
    CatalogClient catalog;

    @Autowired
    MockMvc mvc;

    final String cashierA = TestTokens.token(1, 40, "GUEST", "SALE_CREATE");
    final String cashierB = TestTokens.token(2, 40, "GUEST", "SALE_CREATE");
    final String admin = TestTokens.token(3, 40, "ADMIN", "SALE_CREATE", "SALE_CANCEL", "SALES_VIEW_ALL");
    final String otherStore = TestTokens.token(4, 41, "ADMIN", "SALE_CREATE", "SALE_CANCEL", "SALES_VIEW_ALL");

    static final PricedLine MILK = new PricedLine(10, "Lait", 1, "Crèmerie", new BigDecimal("100.00"), 20, new BigDecimal("80.00"));
    static final PricedLine BREAD = new PricedLine(11, "Pain", 2, "Boulangerie", new BigDecimal("15.50"), 0, new BigDecimal("15.50"));

    @Test
    void encaissement_ticket_visibilite_etAnnulation() throws Exception {
        when(catalog.decrement(any())).thenReturn(List.of(MILK, BREAD));

        // 2 laits (dont une ligne en double dans le panier) + 1 pain = 2 × 80 + 15.50 = 175.50
        String created = sale(cashierA, "[{\"productId\":10,\"qty\":1},{\"productId\":11,\"qty\":1},{\"productId\":10,\"qty\":1}]", "200")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(175.50))
                .andExpect(jsonPath("$.changeGiven").value(24.50))
                .andExpect(jsonPath("$.lines", hasSize(2)))
                .andExpect(jsonPath("$.lines[0].discountPercent").value(20))
                .andReturn().getResponse().getContentAsString();
        int saleA = JsonPath.read(created, "$.id");
        verify(catalog).decrement(new StockRequest(List.of(
                new com.store.common.contract.StockContract.Line(10, 2),
                new com.store.common.contract.StockContract.Line(11, 1))));

        when(catalog.decrement(any())).thenReturn(List.of(BREAD));
        sale(cashierB, "[{\"productId\":11,\"qty\":2}]", "31").andExpect(status().isCreated());

        // chaque caissier ne voit que ses ventes ; l'admin voit tout le store ; l'autre store rien
        call(get("/sales/sales"), cashierA, null).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].itemCount").value(3));
        call(get("/sales/sales"), admin, null).andExpect(jsonPath("$", hasSize(2)));
        call(get("/sales/sales?cashierId=1"), admin, null).andExpect(jsonPath("$", hasSize(1)));
        call(get("/sales/sales/" + saleA), cashierB, null).andExpect(status().isNotFound());
        call(get("/sales/sales/" + saleA), otherStore, null).andExpect(status().isNotFound());

        // annulation : permission requise, une seule fois, événement publié pour recréditer le stock
        call(post("/sales/sales/" + saleA + "/cancel"), cashierA, null).andExpect(status().isForbidden());
        call(post("/sales/sales/" + saleA + "/cancel"), admin, null).andExpect(jsonPath("$.status").value("CANCELLED"));
        call(post("/sales/sales/" + saleA + "/cancel"), admin, null).andExpect(jsonPath("$.code").value("ALREADY_CANCELLED"));
        call(post("/sales/sales/" + saleA + "/cancel"), otherStore, null).andExpect(status().isNotFound());

        var completed = awaitRecord(Events.SALE_COMPLETED, r -> r.value().contains("\"saleId\":" + saleA + ","));
        assertThat(JsonPath.<Double>read(completed.value(), "$.total")).isEqualTo(175.5);
        var cancelled = awaitRecord(Events.SALE_CANCELLED, r -> r.value().contains("\"saleId\":" + saleA + ","));
        assertThat(cancelled.headers().lastHeader(Events.EVENT_ID_HEADER)).isNotNull();
    }

    @Test
    void especesInsuffisantes_ouStockInsuffisant_rienNEstEnregistre() throws Exception {
        when(catalog.decrement(any())).thenReturn(List.of(MILK));
        sale(cashierA, "[{\"productId\":10,\"qty\":1}]", "50")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INSUFFICIENT_CASH"));
        verify(catalog).increment(any()); // compensation : le stock sorti est rendu

        // l'erreur métier du catalogue arrive telle quelle à la caisse
        byte[] body = """
                {"code":"INSUFFICIENT_STOCK","message":"Stock insuffisant pour « Lait » (disponible : 0)","details":{"available":0}}"""
                .getBytes(StandardCharsets.UTF_8);
        when(catalog.decrement(any())).thenThrow(new FeignException.Conflict("conflict",
                Request.create(Request.HttpMethod.POST, "/internal/stock/decrement", Map.of(), null, StandardCharsets.UTF_8, null),
                body, Map.of()));
        sale(cashierA, "[{\"productId\":10,\"qty\":1}]", "500")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Stock insuffisant pour « Lait » (disponible : 0)"));

        sale(cashierA, "[]", "10").andExpect(status().isBadRequest());
        sale(cashierA, "[{\"productId\":10,\"qty\":0}]", "10").andExpect(status().isBadRequest());
    }

    private ResultActions sale(String token, String lines, String cash) throws Exception {
        return call(post("/sales/sales"), token, "{\"lines\":%s,\"cashReceived\":%s}".formatted(lines, cash));
    }

    private ResultActions call(MockHttpServletRequestBuilder req, String token, String body) throws Exception {
        req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(req);
    }
}
