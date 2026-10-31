package com.store.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

/**
 * Offre gratuite Render : un service sans trafic s'endort après 15 min et met 1 à 3 min à se réveiller.
 * Tant que la gateway est réveillée (quelqu'un utilise l'application), elle réveille tous les services
 * en même temps puis les garde éveillés ; quand plus personne ne l'appelle, elle s'endort et eux aussi.
 */
// ponytail: compromis pour l'offre gratuite (750 h/mois partagées) ; inutile sur un plan payant, supprimer le profil
@Component
@Profile("render")
@EnableScheduling
public class WakeUpPinger {

    private static final Logger log = LoggerFactory.getLogger(WakeUpPinger.class);

    private final WebClient http = WebClient.create();
    private final List<String> services;

    public WakeUpPinger(@Value("${AUTH_URL}") String auth, @Value("${CATALOG_URL}") String catalog,
                        @Value("${SUPPLIER_URL}") String supplier, @Value("${SALES_URL}") String sales,
                        @Value("${REPORTING_URL}") String reporting) {
        this.services = List.of(auth, catalog, supplier, sales, reporting);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void wakeAllOnStartup() {
        pingAll();
    }

    /** Toutes les 10 min : sous les 15 min d'inactivité qui endorment un service gratuit. */
    @Scheduled(initialDelay = 600_000, fixedDelay = 600_000)
    public void keepAwake() {
        pingAll();
    }

    private void pingAll() {
        for (String url : services) {
            http.get().uri(url + "/actuator/health").retrieve().toBodilessEntity()
                    .timeout(Duration.ofMinutes(3))
                    .subscribe(ok -> { }, e -> log.debug("Réveil de {} : {}", url, e.toString()));
        }
    }
}
