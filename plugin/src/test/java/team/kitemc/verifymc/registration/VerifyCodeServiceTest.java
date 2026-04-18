package team.kitemc.verifymc.registration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VerifyCodeServiceTest {
    @Test
    void shouldIsolateCodesByPurpose() {
        VerifyCodeService service = new VerifyCodeService();
        try {
            VerifyCodeService.CodeIssueResult registerCode = service.issueCode(VerifyCodePurpose.REGISTER, "steve@example.com");
            VerifyCodeService.CodeIssueResult changePasswordCode = service.issueCode(VerifyCodePurpose.CHANGE_PASSWORD, "alex@example.com");

            assertTrue(registerCode.issued());
            assertTrue(changePasswordCode.issued());
            assertNotNull(registerCode.code());
            assertNotNull(changePasswordCode.code());

            assertTrue(service.checkCode(VerifyCodePurpose.REGISTER, "steve@example.com", registerCode.code()));
            assertFalse(service.checkCode(VerifyCodePurpose.CHANGE_PASSWORD, "steve@example.com", registerCode.code()));
            assertTrue(service.checkCode(VerifyCodePurpose.CHANGE_PASSWORD, "alex@example.com", changePasswordCode.code()));
            assertFalse(service.checkCode(VerifyCodePurpose.REGISTER, "alex@example.com", changePasswordCode.code()));
        } finally {
            service.stop();
        }
    }
}
