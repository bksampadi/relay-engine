package dev.bksampadi.relay.delivery;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;

@Service
public class DeliveryClaimService {

    private static final Duration LEASE_DURATION = Duration.ofSeconds(30);

    private final DeliveryRepository deliveryRepository;

    public DeliveryClaimService(DeliveryRepository deliveryRepository) {
        this.deliveryRepository = deliveryRepository;
    }

    @Transactional
    public Optional<Delivery> claimNext() {
        return deliveryRepository.claimNextReady(LEASE_DURATION);
    }
}