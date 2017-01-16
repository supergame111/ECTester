package applets;


import javacard.framework.ISO7816;
import javacard.security.*;

/**
 * Class capable of testing ECDH/C and ECDSA.
 * Note that ECDH and ECDHC output should equal, only the algorithm is different.
 */
public class ECKeyTester {
    private KeyAgreement ecdhKeyAgreement = null;
    private KeyAgreement ecdhcKeyAgreement = null;
    private Signature ecdsaSignature = null;

    public short allocateECDH() {
        short result = ISO7816.SW_NO_ERROR;
        try {
            ecdhKeyAgreement = KeyAgreement.getInstance(KeyAgreement.ALG_EC_SVDP_DH, false);
        } catch (CryptoException ce) {
            result = ce.getReason();
        } catch (Exception e) {
            result = ISO7816.SW_UNKNOWN;
        }
        return result;
    }

    public short allocateECDHC() {
        short result = ISO7816.SW_NO_ERROR;
        try {
            ecdhcKeyAgreement = KeyAgreement.getInstance(KeyAgreement.ALG_EC_SVDP_DHC, false);
        } catch (CryptoException ce) {
            result = ce.getReason();
        } catch (Exception e) {
            result = ISO7816.SW_UNKNOWN;
        }
        return result;
    }

    public short allocateECDSA() {
        short result = ISO7816.SW_NO_ERROR;
        try {
            ecdsaSignature = Signature.getInstance(Signature.ALG_ECDSA_SHA, false);
        } catch (CryptoException ce) {
            result = ce.getReason();
        } catch (Exception e) {
            result = ISO7816.SW_UNKNOWN;
        }
        return result;
    }

    private short testKA(KeyAgreement ka, ECPrivateKey privateKey, byte[] pubkeyBuffer, short pubkeyOffset, short pubkeyLength, byte[] outputBuffer, short outputOffset) {
        short result = ISO7816.SW_NO_ERROR;
        try {
            ka.init(privateKey);

            short secretLength = ka.generateSecret(pubkeyBuffer, pubkeyOffset, pubkeyLength, outputBuffer, outputOffset);
            //TODO, figure out how to separate the return value of this method (short) error, and return the secretLenght..
        } catch (CryptoException ce) {
            result = ce.getReason();
        } catch (Exception e) {
            result = ISO7816.SW_UNKNOWN;
        }
        return result;
    }

    private short testKA_validPoint(KeyAgreement ka, ECPrivateKey privateKey, byte[] pubkeyBuffer, short pubkeyOffset, short pubkeyLength, byte[] outputBuffer, short outputOffset) {
        return testKA(ka, privateKey, pubkeyBuffer, pubkeyOffset, pubkeyLength, outputBuffer, outputOffset);
    }

    private short testKA_invalidPoint(KeyAgreement ka, ECPrivateKey privateKey, byte[] pubkeyBuffer, short pubkeyOffset, short pubkeyLength, byte[] outputBuffer, short outputOffset) {
        pubkeyBuffer[(short)(pubkeyLength - 2)] += 0xcc;
        pubkeyBuffer[(short)(pubkeyLength - 3)] += 0xcc;
        short result = testKA(ka, privateKey, pubkeyBuffer, pubkeyOffset, pubkeyLength, outputBuffer, outputOffset);
        pubkeyBuffer[(short)(pubkeyLength - 2)] -= 0xcc;
        pubkeyBuffer[(short)(pubkeyLength - 3)] -= 0xcc;
        return result;
    }

    public short testECDH(ECPrivateKey privateKey, byte[] pubkeyBuffer, short pubkeyOffset, short pubkeyLength, byte[] outputBuffer, short outputOffset) {
        return testKA(ecdhKeyAgreement, privateKey, pubkeyBuffer, pubkeyOffset, pubkeyLength, outputBuffer, outputOffset);
    }

    /**
     * Tests ECDH secret generation with given {@code privateKey} and {@code publicKey}.
     * Uses {@code pubkeyBuffer} at {@code pubkeyOffset} for computations.
     * Output should equal with ECDHC output.
     * @param privateKey
     * @param publicKey
     * @param pubkeyBuffer
     * @param pubkeyOffset
     * @param outputBuffer
     * @param outputOffset
     * @return  ISO7816.SW_NO_ERROR on correct operation,
     *          exception reason otherwise
     **/
    public short testECDH_validPoint(ECPrivateKey privateKey, ECPublicKey publicKey, byte[] pubkeyBuffer, short pubkeyOffset, byte[] outputBuffer, short outputOffset) {
        short length = publicKey.getW(pubkeyBuffer, pubkeyOffset);
        return testKA_validPoint(ecdhKeyAgreement, privateKey, pubkeyBuffer, pubkeyOffset, length, outputBuffer, outputOffset);
    }

    public short testECDH_invalidPoint(ECPrivateKey privateKey, ECPublicKey publicKey, byte[] pubkeyBuffer, short pubkeyOffset, byte[] outputBuffer, short outputOffset) {
        short length = publicKey.getW(pubkeyBuffer, pubkeyOffset);
        return testKA_invalidPoint(ecdhKeyAgreement, privateKey, pubkeyBuffer, pubkeyOffset, length, outputBuffer, outputOffset);
    }


    /**
     * Tests ECDHC secret generation with given {@code privateKey} and {@code publicKey}.
     * Uses {@code pubkeyBuffer} at {@code pubkeyOffset} for computations.
     * Output should equal to ECDH output.
     * @param privateKey
     * @param publicKey
     * @param pubkeyBuffer
     * @param pubkeyOffset
     * @param outputBuffer
     * @param outputOffset
     * @return  ISO7816.SW_NO_ERROR on correct operation,
     *          exception reason otherwise
     */
    public short testECDHC_validPoint(ECPrivateKey privateKey, ECPublicKey publicKey, byte[] pubkeyBuffer, short pubkeyOffset, byte[] outputBuffer, short outputOffset) {
        short length = publicKey.getW(pubkeyBuffer, pubkeyOffset);
        return testKA_validPoint(ecdhcKeyAgreement, privateKey, pubkeyBuffer, pubkeyOffset, length, outputBuffer, outputOffset);
    }

    public short testECDHC_invalidPoint(ECPrivateKey privateKey, ECPublicKey publicKey, byte[] pubkeyBuffer, short pubkeyOffset, byte[] outputBuffer, short outputOffset) {
        short length = publicKey.getW(pubkeyBuffer, pubkeyOffset);
        return testKA_invalidPoint(ecdhcKeyAgreement, privateKey, pubkeyBuffer, pubkeyOffset, length, outputBuffer, outputOffset);
    }

    /**
     * Uses {@code signKey} to sign data from {@code inputBuffer} at {@code inputOffset} with {@code inputOffset}.
     * Then checks for correct signature length.
     * Then tries verifying the data with {@code verifyKey}.
     * @param signKey
     * @param verifyKey
     * @param inputBuffer
     * @param inputOffset
     * @param inputLength
     * @param sigBuffer
     * @param sigOffset
     * @return  ISO7816.SW_NO_ERROR on correct operation,
     *          SW_SIG_VERIFY_FAIL,
     *          SW_SIG_LENGTH_MISMATCH
     */
    public short testECDSA(ECPrivateKey signKey, ECPublicKey verifyKey, byte[] inputBuffer, short inputOffset, short inputLength, byte[] sigBuffer, short sigOffset) {
        short result = ISO7816.SW_NO_ERROR;
        try {
            ecdsaSignature.init(signKey, Signature.MODE_SIGN);
            short sigLength = ecdsaSignature.sign(inputBuffer, inputOffset, inputLength, sigBuffer, sigOffset);

            ecdsaSignature.init(verifyKey, Signature.MODE_VERIFY);
            boolean correct = ecdsaSignature.verify(inputBuffer, inputOffset, inputLength, sigBuffer, sigOffset, sigLength);
            if (!correct) {
                result = SimpleECCApplet.SW_SIG_VERIFY_FAIL;
            }
        } catch (CryptoException ce) {
            result = ce.getReason();
        } catch (Exception e) {
            result = ISO7816.SW_UNKNOWN;
        }
        return result;
    }

    public KeyAgreement getECDH() {
        return ecdhKeyAgreement;
    }

    public KeyAgreement getECDHC() {
        return ecdhcKeyAgreement;
    }

    public Signature getECDSA() {
        return ecdsaSignature;
    }

}