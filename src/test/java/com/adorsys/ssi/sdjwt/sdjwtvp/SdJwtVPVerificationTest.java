
package com.adorsys.ssi.sdjwt.sdjwtvp;

import com.adorsys.ssi.sdjwt.IssuerSignedJwtVerificationOpts;
import com.adorsys.ssi.sdjwt.SdJwt;
import com.adorsys.ssi.sdjwt.TestSettings;
import com.adorsys.ssi.sdjwt.TestUtils;
import com.adorsys.ssi.sdjwt.exception.SdJwtVerificationException;
import com.adorsys.ssi.sdjwt.vp.KeyBindingJWT;
import com.adorsys.ssi.sdjwt.vp.KeyBindingJwtVerificationOpts;
import com.adorsys.ssi.sdjwt.vp.SdJwtVP;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSSigner;
import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

/**
 * @author <a href="mailto:Ingrid.Kamga@adorsys.com">Ingrid Kamga</a>
 */
public class SdJwtVPVerificationTest {
    static ObjectMapper mapper = new ObjectMapper();
    static TestSettings testSettings = TestSettings.getInstance();

    @Test
    public void settingsTest() {
        JWSSigner issuerSignerContext = testSettings.issuerSigContext.signer;
        assertNotNull(issuerSignerContext);
    }

    @Test
    public void testVerif_s20_1_sdjwt_with_kb() throws SdJwtVerificationException {
        String sdJwtVPString = TestUtils.readFileAsString(getClass(), "sdjwt/s20.1-sdjwt+kb.txt");
        SdJwtVP sdJwtVP = SdJwtVP.of(sdJwtVPString);

        sdJwtVP.verify(
                defaultIssuerSignedJwtVerificationOpts().build(),
                defaultKeyBindingJwtVerificationOpts().build()
        );
    }

    @Test
    public void testVerifKeyBindingNotRequired() throws SdJwtVerificationException {
        String sdJwtVPString = TestUtils.readFileAsString(getClass(), "sdjwt/s6.2-presented-sdjwtvp.txt");
        SdJwtVP sdJwtVP = SdJwtVP.of(sdJwtVPString);

        sdJwtVP.verify(
                defaultIssuerSignedJwtVerificationOpts().build(),
                defaultKeyBindingJwtVerificationOpts()
                        .withKeyBindingRequired(false)
                        .build()
        );
    }

    @Test
    public void testShouldFail_IfExtraDisclosureWithNoDigest() {
        testShouldFailGeneric(
                // One disclosure has no digest throughout Issuer-signed JWT
                "sdjwt/s20.6-sdjwt+kb--disclosure-with-no-digest.txt",
                defaultKeyBindingJwtVerificationOpts().build(),
                "At least one disclosure is not protected by digest",
                null
        );
    }

    @Test
    public void testShouldFail_IfFieldDisclosureLengthIncorrect() {
        testShouldFailGeneric(
                // One field disclosure has only two elements
                "sdjwt/s20.7-sdjwt+kb--invalid-field-disclosure.txt",
                defaultKeyBindingJwtVerificationOpts().build(),
                "A field disclosure must contain exactly three elements",
                null
        );
    }

    @Test
    public void testShouldFail_IfArrayElementDisclosureLengthIncorrect() {
        testShouldFailGeneric(
                // One array element disclosure has more than two elements
                "sdjwt/s20.7-sdjwt+kb--invalid-array-elt-disclosure.txt",
                defaultKeyBindingJwtVerificationOpts().build(),
                "An array element disclosure must contain exactly two elements",
                null
        );
    }

    @Test
    public void testShouldFail_IfKeyBindingRequiredAndMissing() {
        testShouldFailGeneric(
                // This sd-jwt has no key binding jwt
                "sdjwt/s6.2-presented-sdjwtvp.txt",
                defaultKeyBindingJwtVerificationOpts()
                        .withKeyBindingRequired(true)
                        .build(),
                "Missing Key Binding JWT",
                null
        );
    }

    @Test
    public void testShouldFail_IfKeyBindingJwtSignatureInvalid() {
        testShouldFailGeneric(
                // Messed up with the kb signature
                "sdjwt/s20.1-sdjwt+kb--wrong-kb-signature.txt",
                defaultKeyBindingJwtVerificationOpts().build(),
                "Key binding JWT invalid",
                "Invalid JWS signature"
        );
    }

    @Test
    public void testShouldFail_IfNoCnfClaim() {
        testShouldFailGeneric(
                // This test vector has no cnf claim in Issuer-signed JWT
                "sdjwt/s20.2-sdjwt+kb--no-cnf-claim.txt",
                defaultKeyBindingJwtVerificationOpts().build(),
                "No cnf claim in Issuer-signed JWT for key binding",
                null
        );
    }

    @Test
    public void testShouldFail_IfWrongKbTyp() {
        testShouldFailGeneric(
                // Key Binding JWT's header: {"kid": "holder", "typ": "unexpected",  "alg": "ES256"}
                "sdjwt/s20.3-sdjwt+kb--wrong-kb-typ.txt",
                defaultKeyBindingJwtVerificationOpts().build(),
                "Key Binding JWT is not of declared typ kb+jwt",
                null
        );
    }

    @Test
    public void testShouldFail_IfReplayChecksFail_Nonce() {
        testShouldFailGeneric(
                "sdjwt/s20.1-sdjwt+kb.txt",
                defaultKeyBindingJwtVerificationOpts()
                        .withNonce("abcd") // kb's nonce is "1234567890"
                        .build(),
                "Key binding JWT: Unexpected `nonce` value",
                null
        );
    }

    @Test
    public void testShouldFail_IfReplayChecksFail_Aud() {
        testShouldFailGeneric(
                "sdjwt/s20.1-sdjwt+kb.txt",
                defaultKeyBindingJwtVerificationOpts()
                        .withAud("abcd") // kb's aud is "https://verifier.example.org"
                        .build(),
                "Key binding JWT: Unexpected `aud` value",
                null
        );
    }

    @Test
    public void testShouldFail_IfKbSdHashWrongFormat() {
        var kbPayload = exampleS20KbPayload();

        // This hash is not a string
        kbPayload.set("sd_hash", mapper.valueToTree(1234));

        testShouldFailGeneric2(
                kbPayload,
                "sdjwt/s20.1-sdjwt+kb.txt",
                defaultKeyBindingJwtVerificationOpts().build(),
                "Key binding JWT: Claim `sd_hash` missing or not a string",
                null
        );
    }

    @Test
    public void testShouldFail_IfKbSdHashInvalid() {
        var kbPayload = exampleS20KbPayload();

        // This hash makes no sense
        kbPayload.put("sd_hash", "c3FmZHFmZGZlZXNkZmZi");

        testShouldFailGeneric2(
                kbPayload,
                "sdjwt/s20.1-sdjwt+kb.txt",
                defaultKeyBindingJwtVerificationOpts().build(),
                "Key binding JWT: Invalid `sd_hash` digest",
                null
        );
    }

    @Test
    public void testShouldFail_IfKbIssuedInFuture() {
        long now = Instant.now().getEpochSecond();

        var kbPayload = exampleS20KbPayload();
        kbPayload.set("iat", mapper.valueToTree(now + 1000));

        testShouldFailGeneric2(
                kbPayload,
                "sdjwt/s20.1-sdjwt+kb.txt",
                defaultKeyBindingJwtVerificationOpts().build(),
                "Key binding JWT: Invalid `iat` claim",
                "jwt issued in the future"
        );
    }

    @Test
    public void testShouldFail_IfKbIssuedBeforeIssuerSignedJwt() {
        long issuerSignedJwtIat = 1683000000; // same value in test vector

        var kbPayload = exampleS20KbPayload();
        kbPayload.set("iat", mapper.valueToTree(issuerSignedJwtIat - 1000));

        testShouldFailGeneric2(
                kbPayload,
                "sdjwt/s20.1-sdjwt+kb.txt",
                defaultKeyBindingJwtVerificationOpts().build(),
                "Key binding JWT was issued before Issuer-signed JWT",
                null
        );
    }

    @Test
    public void testShouldFail_IfKbExpired() {
        long now = Instant.now().getEpochSecond();

        var kbPayload = exampleS20KbPayload();
        kbPayload.set("exp", mapper.valueToTree(now - 1000));

        testShouldFailGeneric2(
                kbPayload,
                // No iat in issuer jwt so this test cover that code branch
                "sdjwt/s20.4-sdjwt+kb--no-iat-in-issuer-jwt.txt",
                defaultKeyBindingJwtVerificationOpts()
                        .withValidateExpirationClaim(true)
                        .build(),
                "Key binding JWT: Invalid `exp` claim",
                "jwt has expired"
        );
    }

    @Test
    public void testShouldFail_IfKbNotBeforeTimeYet() {
        long now = Instant.now().getEpochSecond();

        var kbPayload = exampleS20KbPayload();
        kbPayload.set("nbf", mapper.valueToTree(now + 1000));

        testShouldFailGeneric2(
                kbPayload,
                "sdjwt/s20.4-sdjwt+kb--no-iat-in-issuer-jwt.txt",
                defaultKeyBindingJwtVerificationOpts()
                        .withValidateNotBeforeClaim(true)
                        .build(),
                "Key binding JWT: Invalid `nbf` claim",
                "jwt not valid yet"
        );
    }

    private void testShouldFailGeneric(
            String testFilePath,
            KeyBindingJwtVerificationOpts keyBindingJwtVerificationOpts,
            String exceptionMessage,
            String exceptionCauseMessage
    ) {
        String sdJwtVPString = TestUtils.readFileAsString(getClass(), testFilePath);
        SdJwtVP sdJwtVP = SdJwtVP.of(sdJwtVPString);

        var exception = assertThrows(
                SdJwtVerificationException.class,
                () -> sdJwtVP.verify(
                        defaultIssuerSignedJwtVerificationOpts().build(),
                        keyBindingJwtVerificationOpts
                )
        );

        assertEquals(exceptionMessage, exception.getMessage());
        if (exceptionCauseMessage != null) {
            assertEquals(exceptionCauseMessage, exception.getCause().getMessage());
        }
    }

    private void testShouldFailGeneric2(
            JsonNode kbPayloadSubstitute,
            String testFilePath,
            KeyBindingJwtVerificationOpts keyBindingJwtVerificationOpts,
            String exceptionMessage,
            String exceptionCauseMessage
    ) {
        KeyBindingJWT keyBindingJWT = KeyBindingJWT.from(
                kbPayloadSubstitute,
                testSettings.holderSigContext.signer,
                "holder",
                JWSAlgorithm.ES256,
                KeyBindingJWT.TYP
        );

        String sdJwtVPString = TestUtils.readFileAsString(getClass(), testFilePath);
        SdJwtVP sdJwtVP = SdJwtVP.of(
                sdJwtVPString.substring(0, sdJwtVPString.lastIndexOf(SdJwt.DELIMITER) + 1)
                + keyBindingJWT.toJws()
        );

        var exception = assertThrows(
                SdJwtVerificationException.class,
                () -> sdJwtVP.verify(
                        defaultIssuerSignedJwtVerificationOpts().build(),
                        keyBindingJwtVerificationOpts
                )
        );

        assertEquals(exceptionMessage, exception.getMessage());
        if (exceptionCauseMessage != null) {
            assertEquals(exceptionCauseMessage, exception.getCause().getMessage());
        }
    }

    private IssuerSignedJwtVerificationOpts.Builder defaultIssuerSignedJwtVerificationOpts() {
        return IssuerSignedJwtVerificationOpts.builder()
                .withVerifier(testSettings.issuerVerifierContext.verifier)
                .withValidateIssuedAtClaim(false)
                .withValidateNotBeforeClaim(false);
    }

    private KeyBindingJwtVerificationOpts.Builder defaultKeyBindingJwtVerificationOpts() {
        return KeyBindingJwtVerificationOpts.builder()
                .withKeyBindingRequired(true)
                .withNonce("1234567890")
                .withAud("https://verifier.example.org")
                .withValidateExpirationClaim(false)
                .withValidateNotBeforeClaim(false);
    }

    private ObjectNode exampleS20KbPayload() {
        var payload = mapper.createObjectNode();
        payload.put("nonce", "1234567890");
        payload.put("aud", "https://verifier.example.org");
        payload.put("sd_hash", "X9RrrfWt_70gHzOcovGSIt4Fms9Tf2g2hjlWVI_cxZg");
        payload.set("iat", mapper.valueToTree(1702315679));

        return payload;
    }
}
