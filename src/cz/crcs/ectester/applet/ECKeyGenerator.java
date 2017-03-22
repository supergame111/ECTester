package cz.crcs.ectester.applet;

import javacard.framework.CardRuntimeException;
import javacard.framework.ISO7816;
import javacard.framework.ISOException;
import javacard.framework.Util;
import javacard.security.ECPrivateKey;
import javacard.security.ECPublicKey;
import javacard.security.KeyPair;

/**
 * @author Jan Jancar johny@neuromancer.sk
 */
public class ECKeyGenerator {

    private short sw = ISO7816.SW_NO_ERROR;

    /**
     * @param keyClass
     * @param keyLength
     * @return
     */
    public KeyPair allocatePair(byte keyClass, short keyLength) {
        sw = ISO7816.SW_NO_ERROR;
        KeyPair ecKeyPair = null;
        try {
            ecKeyPair = new KeyPair(keyClass, keyLength);

            if (ecKeyPair.getPublic() == null || ecKeyPair.getPrivate() == null) {
                try {
                    ecKeyPair.genKeyPair();
                } catch (Exception ignored) {
                }
            }
        } catch (CardRuntimeException ce) {
            sw = ce.getReason();
        }
        return ecKeyPair;
    }

    public short clearPair(KeyPair keypair, byte key) {
        sw = ISO7816.SW_NO_ERROR;
        try {
            if ((key & EC_Consts.KEY_PUBLIC) != 0) keypair.getPublic().clearKey();
            if ((key & EC_Consts.KEY_PRIVATE) != 0) keypair.getPrivate().clearKey();
        } catch (CardRuntimeException ce) {
            sw = ce.getReason();
        }
        return sw;
    }

    /**
     * @param keypair
     * @return
     */
    public short generatePair(KeyPair keypair) {
        sw = ISO7816.SW_NO_ERROR;
        try {
            keypair.genKeyPair();
        } catch (CardRuntimeException ce) {
            sw = ce.getReason();
        }
        return sw;
    }

    public short setCurve(KeyPair keypair, byte curve, byte[] buffer, short offset) {
        return setCurve(keypair, curve, EC_Consts.PARAMETERS_ALL, buffer, offset);
    }

    public short setCurve(KeyPair keypair, byte curve, short params, byte[] buffer, short offset) {
        return setCurve(keypair, EC_Consts.KEY_BOTH, curve, params, buffer, offset);
    }

    public short setCurve(KeyPair keypair, byte key, byte curve, short params, byte[] buffer, short offset) {
        byte alg = EC_Consts.getCurveType(curve);
        sw = ISO7816.SW_NO_ERROR;

        if (params == EC_Consts.PARAMETERS_NONE) {
            return sw;
        }

        short length;
        //handle fp and f2m differently, as a FP KeyPair doesnt contain a F2M field and vice versa.
        if (alg == KeyPair.ALG_EC_FP && (params & EC_Consts.PARAMETER_FP) != 0) {
            length = EC_Consts.getCurveParameter(curve, EC_Consts.PARAMETER_FP, buffer, offset);
            sw = setParameter(keypair, key, EC_Consts.PARAMETER_FP, buffer, offset, length);
        } else if (alg == KeyPair.ALG_EC_F2M && (params & EC_Consts.PARAMETER_F2M) != 0) {
            length = EC_Consts.getCurveParameter(curve, EC_Consts.PARAMETER_F2M, buffer, offset);
            sw = setParameter(keypair, key, EC_Consts.PARAMETER_F2M, buffer, offset, length);
        }
        if (sw != ISO7816.SW_NO_ERROR) return sw;

        //go through all params
        short paramMask = EC_Consts.PARAMETER_A;
        while (paramMask <= EC_Consts.PARAMETER_S) {
            short masked = (short) (paramMask & params);
            if (masked != 0) {
                length = EC_Consts.getCurveParameter(curve, masked, buffer, offset);
                sw = setParameter(keypair, key, masked, buffer, offset, length);
                if (sw != ISO7816.SW_NO_ERROR) break;
            }
            paramMask = (short) (paramMask << 1);
        }
        return sw;
    }

    /**
     * @param keypair
     * @param corruptParams
     * @param corruption
     * @param buffer
     * @param offset
     * @return
     */
    public short corruptCurve(KeyPair keypair, short corruptParams, byte corruption, byte[] buffer, short offset) {
        return corruptCurve(keypair, EC_Consts.KEY_BOTH, corruptParams, corruption, buffer, offset);
    }

    /**
     * @param keypair
     * @param key
     * @param corruptParams
     * @param corruption
     * @param buffer
     * @param offset
     * @return
     */
    public short corruptCurve(KeyPair keypair, byte key, short corruptParams, byte corruption, byte[] buffer, short offset) {
        sw = ISO7816.SW_NO_ERROR;
        if (corruptParams == EC_Consts.PARAMETERS_NONE) {
            return sw;
        }

        //go through param bit by bit, and invalidate all selected params
        short paramMask = EC_Consts.PARAMETER_FP;
        while (paramMask <= EC_Consts.PARAMETER_S) {
            short masked = (short) (paramMask & corruptParams);
            if (masked != 0) {
                short length = exportParameter(keypair, key, masked, buffer, offset);
                length = EC_Consts.corruptParameter(corruption, buffer, offset, length);
                sw = setParameter(keypair, key, masked, buffer, offset, length);
                if (sw != ISO7816.SW_NO_ERROR) break;
            }
            paramMask = (short) (paramMask << 1);
        }
        return sw;
    }

    /**
     * @param key
     * @param param
     * @param data
     * @param offset
     * @param length
     * @return
     */
    public short setParameter(KeyPair keypair, byte key, short param, byte[] data, short offset, short length) {
        sw = ISO7816.SW_NO_ERROR;
        ECPublicKey ecPublicKey = (ECPublicKey) keypair.getPublic();
        ECPrivateKey ecPrivateKey = (ECPrivateKey) keypair.getPrivate();

        try {
            switch (param) {
                case EC_Consts.PARAMETER_FP:
                    if ((key & EC_Consts.KEY_PUBLIC) != 0) ecPublicKey.setFieldFP(data, offset, length);
                    if ((key & EC_Consts.KEY_PRIVATE) != 0) ecPrivateKey.setFieldFP(data, offset, length);
                    break;
                case EC_Consts.PARAMETER_F2M:
                    if (length == 4) {
                        short i = Util.makeShort(data[(short) (offset + 2)], data[(short) (offset + 3)]);
                        if ((key & EC_Consts.KEY_PUBLIC) != 0) ecPublicKey.setFieldF2M(i);
                        if ((key & EC_Consts.KEY_PRIVATE) != 0) ecPrivateKey.setFieldF2M(i);
                    } else if (length == 8){
                        short i1 = Util.makeShort(data[(short) (offset + 2)], data[(short) (offset + 3)]);
                        short i2 = Util.makeShort(data[(short) (offset + 4)], data[(short) (offset + 5)]);
                        short i3 = Util.makeShort(data[(short) (offset + 6)], data[(short) (offset + 7)]);
                        if ((key & EC_Consts.KEY_PUBLIC) != 0) ecPublicKey.setFieldF2M(i1, i2, i3);
                        if ((key & EC_Consts.KEY_PRIVATE) != 0) ecPrivateKey.setFieldF2M(i1, i2, i3);
                    } else {
                        sw = ISO7816.SW_UNKNOWN;
                    }
                    break;
                case EC_Consts.PARAMETER_A:
                    if ((key & EC_Consts.KEY_PUBLIC) != 0) ecPublicKey.setA(data, offset, length);
                    if ((key & EC_Consts.KEY_PRIVATE) != 0) ecPrivateKey.setA(data, offset, length);
                    break;
                case EC_Consts.PARAMETER_B:
                    if ((key & EC_Consts.KEY_PUBLIC) != 0) ecPublicKey.setB(data, offset, length);
                    if ((key & EC_Consts.KEY_PRIVATE) != 0) ecPrivateKey.setB(data, offset, length);
                    break;
                case EC_Consts.PARAMETER_G:
                    if ((key & EC_Consts.KEY_PUBLIC) != 0) ecPublicKey.setG(data, offset, length);
                    if ((key & EC_Consts.KEY_PRIVATE) != 0) ecPrivateKey.setG(data, offset, length);
                    break;
                case EC_Consts.PARAMETER_R:
                    if ((key & EC_Consts.KEY_PUBLIC) != 0) ecPublicKey.setR(data, offset, length);
                    if ((key & EC_Consts.KEY_PRIVATE) != 0) ecPrivateKey.setR(data, offset, length);
                    break;
                case EC_Consts.PARAMETER_K:
                    short k = 0;
                    if (length > 2 || length <= 0) {
                        sw = ISO7816.SW_UNKNOWN;
                        break;
                    } else if (length == 2) {
                        k = Util.getShort(data, offset);
                    } else if (length == 1) {
                        k = data[offset];
                    }
                    if ((key & EC_Consts.KEY_PUBLIC) != 0) ecPublicKey.setK(k);
                    if ((key & EC_Consts.KEY_PRIVATE) != 0) ecPrivateKey.setK(k);
                    break;
                case EC_Consts.PARAMETER_S:
                    if ((key & EC_Consts.KEY_PRIVATE) != 0) ecPrivateKey.setS(data, offset, length);
                    break;
                case EC_Consts.PARAMETER_W:
                    if ((key & EC_Consts.KEY_PUBLIC) != 0) ecPublicKey.setW(data, offset, length);
                    break;
                default:
                    ISOException.throwIt(ISO7816.SW_FUNC_NOT_SUPPORTED);
            }
        } catch (CardRuntimeException ce) {
            sw = ce.getReason();
        }
        return sw;
    }

    /**
     * @param keypair
     * @param params
     * @param inBuffer
     * @param inOffset
     * @return
     */
    public short setExternalCurve(KeyPair keypair, short params, byte[] inBuffer, short inOffset) {
        return setExternalCurve(keypair, EC_Consts.KEY_BOTH, params, inBuffer, inOffset);
    }

    /**
     * @param keypair
     * @param key
     * @param params
     * @param inBuffer
     * @param inOffset
     * @return
     */
    public short setExternalCurve(KeyPair keypair, byte key, short params, byte[] inBuffer, short inOffset) {
        sw = ISO7816.SW_NO_ERROR;
        if (params == EC_Consts.PARAMETERS_NONE) {
            return sw;
        }

        short paramMask = EC_Consts.PARAMETER_FP;
        while (paramMask <= EC_Consts.PARAMETER_S) {
            short masked = (short) (paramMask & params);
            if (masked != 0) {
                short paramLength = Util.getShort(inBuffer, inOffset);
                inOffset += 2;
                sw = setParameter(keypair, key, masked, inBuffer, inOffset, paramLength);
                inOffset += paramLength;
                if (sw != ISO7816.SW_NO_ERROR) break;
            }
            paramMask = (short) (paramMask << 1);
        }
        return sw;
    }

    /**
     * Exports a selected parameter from a given keyPairs key.
     *
     * @param keypair      keypair to export from
     * @param key          key to export from (KEY_PUBLIC || KEY_PRIVATE)
     * @param param        parameter to export (EC_Consts.PARAMETER_* || ...)
     * @param outputBuffer buffer to write to
     * @param outputOffset offset to start writing in buffer
     * @return length of data written
     */
    public short exportParameter(KeyPair keypair, byte key, short param, byte[] outputBuffer, short outputOffset) {
        sw = ISO7816.SW_NO_ERROR;
        ECPublicKey ecPublicKey = (ECPublicKey) keypair.getPublic();
        ECPrivateKey ecPrivateKey = (ECPrivateKey) keypair.getPrivate();

        short length = 0;
        try {
            switch (param) {
                case EC_Consts.PARAMETER_FP:
                    if ((key & EC_Consts.KEY_PUBLIC) != 0) length = ecPublicKey.getField(outputBuffer, outputOffset);
                    if ((key & EC_Consts.KEY_PRIVATE) != 0) length = ecPrivateKey.getField(outputBuffer, outputOffset);
                    break;
                case EC_Consts.PARAMETER_F2M:
                    if ((key & EC_Consts.KEY_PUBLIC) != 0) {
                        Util.setShort(outputBuffer, outputOffset, ecPublicKey.getSize());
                        length = 2;
                        length += ecPublicKey.getField(outputBuffer, (short) (outputOffset + 2));
                    }
                    if ((key & EC_Consts.KEY_PRIVATE) != 0) {
                        Util.setShort(outputBuffer, outputOffset, ecPrivateKey.getSize());
                        length = 2;
                        length += ecPrivateKey.getField(outputBuffer, (short) (outputOffset + 2));
                    }
                    break;
                case EC_Consts.PARAMETER_A:
                    if ((key & EC_Consts.KEY_PUBLIC) != 0) length = ecPublicKey.getA(outputBuffer, outputOffset);
                    if ((key & EC_Consts.KEY_PRIVATE) != 0) length = ecPrivateKey.getA(outputBuffer, outputOffset);
                    break;
                case EC_Consts.PARAMETER_B:
                    if ((key & EC_Consts.KEY_PUBLIC) != 0) length = ecPublicKey.getB(outputBuffer, outputOffset);
                    if ((key & EC_Consts.KEY_PRIVATE) != 0) length = ecPrivateKey.getB(outputBuffer, outputOffset);
                    break;
                case EC_Consts.PARAMETER_G:
                    if ((key & EC_Consts.KEY_PUBLIC) != 0) length = ecPublicKey.getG(outputBuffer, outputOffset);
                    if ((key & EC_Consts.KEY_PRIVATE) != 0) length = ecPrivateKey.getG(outputBuffer, outputOffset);
                    break;
                case EC_Consts.PARAMETER_R:
                    if ((key & EC_Consts.KEY_PUBLIC) != 0) length = ecPublicKey.getR(outputBuffer, outputOffset);
                    if ((key & EC_Consts.KEY_PRIVATE) != 0) length = ecPrivateKey.getR(outputBuffer, outputOffset);
                    break;
                case EC_Consts.PARAMETER_K:
                    length = 2;
                    if ((key & EC_Consts.KEY_PUBLIC) != 0)
                        Util.setShort(outputBuffer, outputOffset, ecPublicKey.getK());
                    if ((key & EC_Consts.KEY_PRIVATE) != 0)
                        Util.setShort(outputBuffer, outputOffset, ecPrivateKey.getK());
                    break;
                case EC_Consts.PARAMETER_W:
                    if ((key & EC_Consts.KEY_PUBLIC) != 0) length = ecPublicKey.getW(outputBuffer, outputOffset);
                    break;
                case EC_Consts.PARAMETER_S:
                    if ((key & EC_Consts.KEY_PRIVATE) != 0) length = ecPrivateKey.getS(outputBuffer, outputOffset);
                    break;
                default:
                    ISOException.throwIt(ISO7816.SW_FUNC_NOT_SUPPORTED);
            }
        } catch (CardRuntimeException ce) {
            sw = ce.getReason();
        }
        return length;
    }

    /**
     * Exports selected parameters from a given keyPairs key.
     * Raw parameter data is always prepended by its length as a
     * short value. The order of parameters is the usual one from
     * EC_Consts: field,a,b,g,r,k,w,s.
     *
     * @param keypair keyPair to export from
     * @param key     key to export from (KEY_PUBLIC || KEY_PRIVATE)
     * @param params  params to export (EC_Consts.PARAMETER_* | ...)
     * @param buffer  buffer to export to
     * @param offset  offset to start writing in buffer
     * @return length of data written
     */
    public short exportParameters(KeyPair keypair, byte key, short params, byte[] buffer, short offset) {
        sw = ISO7816.SW_NO_ERROR;
        if (params == EC_Consts.PARAMETERS_NONE) {
            return sw;
        }

        short length = 0;
        short paramMask = EC_Consts.PARAMETER_FP;
        while (paramMask <= EC_Consts.PARAMETER_S) {
            short masked = (short) (paramMask & params);
            if (masked != 0) {
                short len = exportParameter(keypair, key, masked, buffer, (short) (offset + 2));
                if (len == 0) {
                    paramMask = (short) (paramMask << 1);
                    continue;
                }
                Util.setShort(buffer, offset, len);
                offset += len + 2;
                length += len + 2;
            }
            paramMask = (short) (paramMask << 1);
        }
        return length;
    }

    /**
     * Copies this KeyPairs curve parameters to another ECKeyGenerator.
     *
     * @param from   keyPair to copy from
     * @param to     keyPair to copy to
     * @param params parameters to copy
     * @param buffer buffer to use for copying
     * @param offset offset to use in buffer
     * @return sw
     */
    public short copyCurve(KeyPair from, KeyPair to, short params, byte[] buffer, short offset) {
        sw = ISO7816.SW_NO_ERROR;
        try {
            short param = EC_Consts.PARAMETER_FP;
            while (param <= EC_Consts.PARAMETER_K) {
                short masked = (short) (param & params);
                if (masked != 0) {
                    short paramLength = exportParameter(from, EC_Consts.KEY_PUBLIC, masked, buffer, offset);
                    setParameter(to, EC_Consts.KEY_BOTH, masked, buffer, offset, paramLength);
                }
                param = (short) (param << 1);
            }
        } catch (CardRuntimeException ce) {
            sw = ce.getReason();
        }
        return sw;
    }

    public short getSW() {
        return sw;
    }
}
