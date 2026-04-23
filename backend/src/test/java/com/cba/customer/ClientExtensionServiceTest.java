package com.cba.customer;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClientExtensionService — unit tests")
class ClientExtensionServiceTest {

    @Mock ClientIdentifierRepository identifierRepository;
    @Mock ClientAddressRepository addressRepository;
    @Mock EntityManager entityManager;
    @Mock AuditLogService auditLogService;

    @InjectMocks ClientExtensionService service;

    private UUID customerId;
    private Customer customer;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        customer = new Customer();
        customer.setId(customerId);
    }

    @Nested
    @DisplayName("Identifiers")
    class Identifiers {

        @Test
        @DisplayName("listIdentifiers returns page")
        void listIdentifiers_returnsPage() {
            ClientIdentifier ci = new ClientIdentifier();
            ci.setId(UUID.randomUUID());
            when(identifierRepository.findByCustomerId(customerId, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(ci)));
            assertThat(service.listIdentifiers(customerId, Pageable.unpaged()).getContent()).hasSize(1);
        }

        @Test
        @DisplayName("createIdentifier saves and returns identifier")
        void createIdentifier_success() {
            when(entityManager.find(Customer.class, customerId)).thenReturn(customer);
            when(identifierRepository.save(any())).thenAnswer(inv -> {
                ClientIdentifier ci = inv.getArgument(0);
                ci.setId(UUID.randomUUID());
                return ci;
            });

            ClientExtensionService.CreateIdentifierRequest req =
                new ClientExtensionService.CreateIdentifierRequest(
                    UUID.randomUUID(), "ID-12345", "National ID", LocalDate.of(2030, 1, 1)
                );
            ClientIdentifier result = service.createIdentifier(customerId, req);
            assertThat(result).isNotNull();
            verify(identifierRepository).save(any(ClientIdentifier.class));
        }

        @Test
        @DisplayName("createIdentifier throws when customer not found")
        void createIdentifier_customerNotFound_throws() {
            when(entityManager.find(Customer.class, customerId)).thenReturn(null);
            ClientExtensionService.CreateIdentifierRequest req =
                new ClientExtensionService.CreateIdentifierRequest(
                    UUID.randomUUID(), "ID-12345", null, null
                );
            assertThatThrownBy(() -> service.createIdentifier(customerId, req))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("deleteIdentifier removes identifier")
        void deleteIdentifier_success() {
            UUID identifierId = UUID.randomUUID();
            ClientIdentifier ci = new ClientIdentifier();
            ci.setId(identifierId);
            when(identifierRepository.findById(identifierId)).thenReturn(Optional.of(ci));

            assertThatCode(() -> service.deleteIdentifier(customerId, identifierId))
                .doesNotThrowAnyException();
            verify(identifierRepository).delete(ci);
        }

        @Test
        @DisplayName("deleteIdentifier throws when not found")
        void deleteIdentifier_notFound_throws() {
            UUID identifierId = UUID.randomUUID();
            when(identifierRepository.findById(identifierId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.deleteIdentifier(customerId, identifierId))
                .isInstanceOf(CbaException.class);
        }
    }

    @Nested
    @DisplayName("Addresses")
    class Addresses {

        @Test
        @DisplayName("listAddresses returns page")
        void listAddresses_returnsPage() {
            when(addressRepository.findByCustomerId(customerId, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of()));
            assertThat(service.listAddresses(customerId, Pageable.unpaged()).getContent()).isEmpty();
        }

        @Test
        @DisplayName("createAddress saves with default HOME type when null")
        void createAddress_defaultsToHome() {
            when(entityManager.find(Customer.class, customerId)).thenReturn(customer);
            when(addressRepository.save(any())).thenAnswer(inv -> {
                ClientAddress addr = inv.getArgument(0);
                addr.setId(UUID.randomUUID());
                return addr;
            });

            ClientExtensionService.CreateAddressRequest req =
                new ClientExtensionService.CreateAddressRequest(
                    null, "123 Main St", null, null,
                    "Nairobi", null, "00100", "KE"
                );
            ClientAddress result = service.createAddress(customerId, req);
            assertThat(result.getAddressType()).isEqualTo(ClientAddress.AddressType.HOME);
        }

        @Test
        @DisplayName("createAddress saves with provided address type")
        void createAddress_withType() {
            when(entityManager.find(Customer.class, customerId)).thenReturn(customer);
            when(addressRepository.save(any())).thenAnswer(inv -> {
                ClientAddress addr = inv.getArgument(0);
                addr.setId(UUID.randomUUID());
                return addr;
            });

            ClientExtensionService.CreateAddressRequest req =
                new ClientExtensionService.CreateAddressRequest(
                    ClientAddress.AddressType.WORK, "456 Office Rd", null, null,
                    "Nairobi", null, "00200", "KE"
                );
            ClientAddress result = service.createAddress(customerId, req);
            assertThat(result.getAddressType()).isEqualTo(ClientAddress.AddressType.WORK);
        }

        @Test
        @DisplayName("createAddress throws when customer not found")
        void createAddress_customerNotFound_throws() {
            when(entityManager.find(Customer.class, customerId)).thenReturn(null);
            ClientExtensionService.CreateAddressRequest req =
                new ClientExtensionService.CreateAddressRequest(
                    null, "123 Main St", null, null, "Nairobi", null, null, "KE"
                );
            assertThatThrownBy(() -> service.createAddress(customerId, req))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("deleteAddress removes address")
        void deleteAddress_success() {
            UUID addressId = UUID.randomUUID();
            ClientAddress addr = new ClientAddress();
            addr.setId(addressId);
            when(addressRepository.findById(addressId)).thenReturn(Optional.of(addr));

            assertThatCode(() -> service.deleteAddress(customerId, addressId))
                .doesNotThrowAnyException();
            verify(addressRepository).delete(addr);
        }

        @Test
        @DisplayName("deleteAddress throws when not found")
        void deleteAddress_notFound_throws() {
            UUID addressId = UUID.randomUUID();
            when(addressRepository.findById(addressId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.deleteAddress(customerId, addressId))
                .isInstanceOf(CbaException.class);
        }
    }
}
