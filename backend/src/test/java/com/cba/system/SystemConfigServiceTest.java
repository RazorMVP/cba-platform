package com.cba.system;

import com.cba.audit.AuditLogService;
import com.cba.common.exception.CbaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SystemConfigService — unit tests")
class SystemConfigServiceTest {

    @Mock CodeRepository codeRepository;
    @Mock CodeValueRepository codeValueRepository;
    @Mock GlobalConfigurationRepository globalConfigRepository;
    @Mock FundRepository fundRepository;
    @Mock SystemPaymentTypeRepository paymentTypeRepository;
    @Mock AccountNumberFormatRepository accountNumberFormatRepository;
    @Mock AuditLogService auditLogService;

    @InjectMocks SystemConfigService service;

    private UUID codeId;
    private UUID valueId;
    private UUID configId;
    private UUID fundId;
    private UUID ptId;
    private UUID fmtId;

    private Code code;
    private CodeValue codeValue;
    private GlobalConfiguration config;
    private Fund fund;
    private SystemPaymentType paymentType;
    private AccountNumberFormat accountFmt;

    @BeforeEach
    void setUp() {
        codeId = UUID.randomUUID();
        valueId = UUID.randomUUID();
        configId = UUID.randomUUID();
        fundId = UUID.randomUUID();
        ptId = UUID.randomUUID();
        fmtId = UUID.randomUUID();

        code = new Code();
        code.setId(codeId);
        code.setName("CustomerType");
        code.setSystemDefined(false);

        codeValue = new CodeValue();
        codeValue.setId(valueId);
        codeValue.setCode(code);
        codeValue.setLabel("INDIVIDUAL");

        config = new GlobalConfiguration();
        config.setId(configId);
        config.setName("max-loan-amount");
        config.setNumericValue(100000L);
        config.setEnabled(true);

        fund = new Fund();
        fund.setId(fundId);
        fund.setName("World Bank Fund");
        fund.setExternalId("WBF-001");

        paymentType = new SystemPaymentType();
        paymentType.setId(ptId);
        paymentType.setName("Cash");
        paymentType.setSystemDefined(false);

        accountFmt = new AccountNumberFormat();
        accountFmt.setId(fmtId);
        accountFmt.setAccountType(AccountNumberFormat.AccountType.SAVINGS);
        accountFmt.setPrefixType(AccountNumberFormat.PrefixType.NONE);
    }

    @Nested
    @DisplayName("Codes")
    class CodesTests {

        @Test
        @DisplayName("listCodes returns page")
        void listCodes_returnsPage() {
            when(codeRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(code)));

            Page<Code> result = service.listCodes(Pageable.unpaged());
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("getCode returns code when found")
        void getCode_found() {
            when(codeRepository.findById(codeId)).thenReturn(Optional.of(code));
            Code result = service.getCode(codeId);
            assertThat(result.getName()).isEqualTo("CustomerType");
        }

        @Test
        @DisplayName("getCode throws when not found")
        void getCode_notFound_throws() {
            when(codeRepository.findById(codeId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getCode(codeId))
                .isInstanceOf(CbaException.class);
        }

        @Test
        @DisplayName("createCode saves new code")
        void createCode_success() {
            when(codeRepository.existsByName("NewCode")).thenReturn(false);
            when(codeRepository.save(any())).thenReturn(code);

            Code result = service.createCode(new SystemConfigService.CreateCodeRequest("NewCode"));
            assertThat(result).isNotNull();
            verify(codeRepository).save(any(Code.class));
        }

        @Test
        @DisplayName("createCode throws when name already exists")
        void createCode_duplicate_throws() {
            when(codeRepository.existsByName("CustomerType")).thenReturn(true);

            assertThatThrownBy(() -> service.createCode(
                new SystemConfigService.CreateCodeRequest("CustomerType")))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("deleteCode removes non-system-defined code")
        void deleteCode_success() {
            when(codeRepository.findById(codeId)).thenReturn(Optional.of(code));

            assertThatCode(() -> service.deleteCode(codeId))
                .doesNotThrowAnyException();
            verify(codeRepository).delete(code);
        }

        @Test
        @DisplayName("deleteCode throws when code is system-defined")
        void deleteCode_systemDefined_throws() {
            code.setSystemDefined(true);
            when(codeRepository.findById(codeId)).thenReturn(Optional.of(code));

            assertThatThrownBy(() -> service.deleteCode(codeId))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("Cannot delete system-defined");
        }
    }

    @Nested
    @DisplayName("Code Values")
    class CodeValues {

        @Test
        @DisplayName("listCodeValues returns page for code")
        void listCodeValues_returnsPage() {
            when(codeRepository.findById(codeId)).thenReturn(Optional.of(code));
            when(codeValueRepository.findByCodeId(eq(codeId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(codeValue)));

            Page<CodeValue> result = service.listCodeValues(codeId, Pageable.unpaged());
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("createCodeValue saves value under code")
        void createCodeValue_success() {
            when(codeRepository.findById(codeId)).thenReturn(Optional.of(code));
            when(codeValueRepository.save(any())).thenReturn(codeValue);

            CodeValue result = service.createCodeValue(codeId,
                new SystemConfigService.CreateCodeValueRequest("INDIVIDUAL", 1, "Individual customer"));
            assertThat(result).isNotNull();
            verify(codeValueRepository).save(any(CodeValue.class));
        }

        @Test
        @DisplayName("deleteCodeValue removes value")
        void deleteCodeValue_success() {
            when(codeValueRepository.findById(valueId)).thenReturn(Optional.of(codeValue));

            assertThatCode(() -> service.deleteCodeValue(codeId, valueId))
                .doesNotThrowAnyException();
            verify(codeValueRepository).delete(codeValue);
        }
    }

    @Nested
    @DisplayName("Global Configuration")
    class GlobalConfig {

        @Test
        @DisplayName("listConfigs returns all configurations")
        void listConfigs_returnsList() {
            when(globalConfigRepository.findAll()).thenReturn(List.of(config));

            List<GlobalConfiguration> result = service.listConfigs();
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("getConfig returns configuration when found")
        void getConfig_found() {
            when(globalConfigRepository.findById(configId)).thenReturn(Optional.of(config));
            GlobalConfiguration result = service.getConfig(configId);
            assertThat(result.getName()).isEqualTo("max-loan-amount");
        }

        @Test
        @DisplayName("updateConfig saves updated configuration")
        void updateConfig_success() {
            when(globalConfigRepository.findById(configId)).thenReturn(Optional.of(config));
            when(globalConfigRepository.save(any())).thenReturn(config);

            GlobalConfiguration result = service.updateConfig(configId,
                new SystemConfigService.UpdateGlobalConfigRequest(null, 200000L, null, true));
            assertThat(result).isNotNull();
            verify(globalConfigRepository).save(any(GlobalConfiguration.class));
        }
    }

    @Nested
    @DisplayName("Funds")
    class FundsTests {

        @Test
        @DisplayName("listFunds returns page")
        void listFunds_returnsPage() {
            when(fundRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(fund)));

            Page<Fund> result = service.listFunds(Pageable.unpaged());
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("createFund saves new fund")
        void createFund_success() {
            when(fundRepository.existsByName("World Bank Fund")).thenReturn(false);
            when(fundRepository.save(any())).thenReturn(fund);

            Fund result = service.createFund(
                new SystemConfigService.CreateFundRequest("World Bank Fund", "WBF-001"));
            assertThat(result.getName()).isEqualTo("World Bank Fund");
        }

        @Test
        @DisplayName("createFund throws when name already exists")
        void createFund_duplicate_throws() {
            when(fundRepository.existsByName("World Bank Fund")).thenReturn(true);

            assertThatThrownBy(() -> service.createFund(
                new SystemConfigService.CreateFundRequest("World Bank Fund", null)))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("updateFund saves updated fund")
        void updateFund_success() {
            when(fundRepository.findById(fundId)).thenReturn(Optional.of(fund));
            when(fundRepository.save(any())).thenReturn(fund);

            Fund result = service.updateFund(fundId,
                new SystemConfigService.CreateFundRequest("Updated Fund", "WBF-002"));
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("deleteFund removes fund")
        void deleteFund_success() {
            when(fundRepository.findById(fundId)).thenReturn(Optional.of(fund));

            assertThatCode(() -> service.deleteFund(fundId))
                .doesNotThrowAnyException();
            verify(fundRepository).delete(fund);
        }
    }

    @Nested
    @DisplayName("Payment Types")
    class PaymentTypes {

        @Test
        @DisplayName("listPaymentTypes returns page")
        void listPaymentTypes_returnsPage() {
            when(paymentTypeRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(paymentType)));

            Page<SystemPaymentType> result = service.listPaymentTypes(Pageable.unpaged());
            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("createPaymentType saves new payment type")
        void createPaymentType_success() {
            when(paymentTypeRepository.existsByName("Cash")).thenReturn(false);
            when(paymentTypeRepository.save(any())).thenReturn(paymentType);

            SystemPaymentType result = service.createPaymentType(
                new SystemConfigService.CreatePaymentTypeRequest("Cash", "Cash payment", true, 1));
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("createPaymentType throws when name already exists")
        void createPaymentType_duplicate_throws() {
            when(paymentTypeRepository.existsByName("Cash")).thenReturn(true);

            assertThatThrownBy(() -> service.createPaymentType(
                new SystemConfigService.CreatePaymentTypeRequest("Cash", null, true, 1)))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("already exists");
        }

        @Test
        @DisplayName("updatePaymentType updates and saves")
        void updatePaymentType_success() {
            when(paymentTypeRepository.findById(ptId)).thenReturn(Optional.of(paymentType));
            when(paymentTypeRepository.save(any())).thenReturn(paymentType);

            SystemPaymentType result = service.updatePaymentType(ptId,
                new SystemConfigService.CreatePaymentTypeRequest("Cash Updated", null, true, 2));
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("deletePaymentType removes non-system-defined type")
        void deletePaymentType_success() {
            when(paymentTypeRepository.findById(ptId)).thenReturn(Optional.of(paymentType));

            assertThatCode(() -> service.deletePaymentType(ptId))
                .doesNotThrowAnyException();
            verify(paymentTypeRepository).delete(paymentType);
        }

        @Test
        @DisplayName("deletePaymentType throws when system-defined")
        void deletePaymentType_systemDefined_throws() {
            paymentType.setSystemDefined(true);
            when(paymentTypeRepository.findById(ptId)).thenReturn(Optional.of(paymentType));

            assertThatThrownBy(() -> service.deletePaymentType(ptId))
                .isInstanceOf(CbaException.class)
                .hasMessageContaining("Cannot delete system-defined");
        }
    }

    @Nested
    @DisplayName("Account Number Formats")
    class AccountNumberFormats {

        @Test
        @DisplayName("listAccountNumberFormats returns all formats")
        void listFormats_returnsList() {
            when(accountNumberFormatRepository.findAll()).thenReturn(List.of(accountFmt));

            List<AccountNumberFormat> result = service.listAccountNumberFormats();
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("updateAccountNumberFormat saves updated format")
        void updateFormat_success() {
            when(accountNumberFormatRepository.findById(fmtId)).thenReturn(Optional.of(accountFmt));
            when(accountNumberFormatRepository.save(any())).thenReturn(accountFmt);

            AccountNumberFormat result = service.updateAccountNumberFormat(fmtId,
                new SystemConfigService.UpdateAccountNumberFormatRequest(
                    AccountNumberFormat.PrefixType.OFFICE_NAME, "HQ"));
            assertThat(result).isNotNull();
            verify(accountNumberFormatRepository).save(any(AccountNumberFormat.class));
        }

        @Test
        @DisplayName("getAccountNumberFormat throws when not found")
        void getFormat_notFound_throws() {
            when(accountNumberFormatRepository.findById(fmtId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getAccountNumberFormat(fmtId))
                .isInstanceOf(CbaException.class);
        }
    }
}
