package org.talust.common.crypto;

import org.spongycastle.crypto.digests.RIPEMD160Digest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;


public class Utils {

    public static final Charset CHARSET = Charset.forName("UTF-8");
    private static final int MAGIC_8 = 8;
    private static final int MAGIC_0X80 = 0x80;
    /**
     * The string that prefixes all text messages signed using Bitcoin keys.
     */
    public static final String SIGNED_MESSAGE_HEADER = "RiceChain Signed Message:\n";
    public static final byte[] SIGNED_MESSAGE_HEADER_BYTES = SIGNED_MESSAGE_HEADER.getBytes(CHARSET);

    /**
     * MPI encoded numbers are produced by the OpenSSL BN_bn2mpi function. They consist of
     * a 4 byte big endian length field, followed by the stated number of bytes representing
     * the number in big endian format (with a sign bit).
     *
     * @param hasLength can be set to false if the given array is missing the 4 byte length field
     */
    public static BigInteger decodeMPI(byte[] mpi, boolean hasLength) {
        byte[] buf;
        if (hasLength) {
            int length = (int) readUint32BE(mpi, 0);
            buf = new byte[length];
            System.arraycopy(mpi, 4, buf, 0, length);
        } else {
            buf = mpi;
        }
        if (buf.length == 0) {
            return BigInteger.ZERO;
        }
        boolean isNegative = (buf[0] & MAGIC_0X80) == MAGIC_0X80;
        if (isNegative) {
            buf[0] &= 0x7f;
        }
        BigInteger result = new BigInteger(buf);
        return isNegative ? result.negate() : result;
    }

    /**
     * MPI encoded numbers are produced by the OpenSSL BN_bn2mpi function. They consist of
     * a 4 byte big endian length field, followed by the stated number of bytes representing
     * the number in big endian format (with a sign bit).
     *
     * @param includeLength indicates whether the 4 byte length field should be included
     */
    public static byte[] encodeMPI(BigInteger value, boolean includeLength) {
        if (value.equals(BigInteger.ZERO)) {
            if (!includeLength) {
                return new byte[]{};
            } else {
                return new byte[]{0x00, 0x00, 0x00, 0x00};
            }
        }
        boolean isNegative = value.signum() < 0;
        if (isNegative) {
            value = value.negate();
        }
        byte[] array = value.toByteArray();
        int length = array.length;
        if ((array[0] & MAGIC_0X80) == MAGIC_0X80) {
            length++;
        }
        if (includeLength) {
            byte[] result = new byte[length + 4];
            System.arraycopy(array, 0, result, length - array.length + 3, array.length);
            uint32ToByteArrayBE(length, result, 0);
            if (isNegative) {
                result[4] |= MAGIC_0X80;
            }
            return result;
        } else {
            byte[] result;
            if (length != array.length) {
                result = new byte[length];
                System.arraycopy(array, 0, result, 1, array.length);
            } else {
                result = array;
            }
            if (isNegative) {
                result[0] |= MAGIC_0X80;
            }
            return result;
        }
    }

    /**
     * Given a textual message, returns a byte buffer formatted as follows:</p>
     * <p>
     * <tt><p>[24] "Bitcoin Signed Message:\n" [message.length as a varint] message</p></tt>
     */
    public static byte[] formatMessageForSigning(String message) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bos.write(SIGNED_MESSAGE_HEADER_BYTES.length);
            bos.write(SIGNED_MESSAGE_HEADER_BYTES);
            byte[] messageBytes = message.getBytes(CHARSET);
            VarInt size = new VarInt(messageBytes.length);
            bos.write(size.encode());
            bos.write(messageBytes);
            return bos.toByteArray();
        } catch (IOException e) {
            // Cannot happen.
            throw new RuntimeException(e);
        }
    }

    private static int isAndroid = -1;

    public static boolean isAndroidRuntime() {
        if (isAndroid == -1) {
            final String runtime = System.getProperty("java.runtime.name");
            isAndroid = (runtime != null && "Android Runtime".equals(runtime)) ? 1 : 0;
        }
        return isAndroid == 1;
    }

    public static String toString(byte[] bytes, String charsetName) {
        try {
            return new String(bytes, charsetName);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] toBytes(CharSequence str, String charsetName) {
        try {
            return str.toString().getBytes(charsetName);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns a copy of the given byte array in reverse order.
     */
    public static byte[] reverseBytes(byte[] bytes) {
        // We could use the XOR trick here but it's easier to understand if we don't. If we find this is really a
        // performance issue the matter can be revisited.
        byte[] buf = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            buf[i] = bytes[bytes.length - 1 - i];
        }
        return buf;
    }

    /**
     * Parse 4 bytes from the byte array (starting at the offset) as unsigned 32-bit integer in little endian format.
     */
    public static long readUint32LE(byte[] bytes, int offset) {
        return (bytes[offset] & 0xffL) |
                ((bytes[offset + 1] & 0xffL) << 8) |
                ((bytes[offset + 2] & 0xffL) << 16) |
                ((bytes[offset + 3] & 0xffL) << 24);
    }

    public static short readInt16LE(byte[] bytes, int offset) {
        return (short) ((bytes[offset] & 0xff) |
                ((bytes[offset + 1] & 0xff) << 8));
    }

    public static int readInt32LE(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) |
                ((bytes[offset + 1] & 0xff) << 8) |
                ((bytes[offset + 2] & 0xff) << 16) |
                ((bytes[offset + 3] & 0xff) << 24);
    }


    /**
     * Parse 8 bytes from the byte array (starting at the offset) as signed 64-bit integer in little endian format.
     */
    public static long readInt64LE(byte[] bytes, int offset) {
        return (bytes[offset] & 0xffL) |
                ((bytes[offset + 1] & 0xffL) << 8) |
                ((bytes[offset + 2] & 0xffL) << 16) |
                ((bytes[offset + 3] & 0xffL) << 24) |
                ((bytes[offset + 4] & 0xffL) << 32) |
                ((bytes[offset + 5] & 0xffL) << 40) |
                ((bytes[offset + 6] & 0xffL) << 48) |
                ((bytes[offset + 7] & 0xffL) << 56);
    }

    /**
     * Parse 4 bytes from the byte array (starting at the offset) as unsigned 32-bit integer in big endian format.
     */
    public static long readUint32BE(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xffL) << 24) |
                ((bytes[offset + 1] & 0xffL) << 16) |
                ((bytes[offset + 2] & 0xffL) << 8) |
                (bytes[offset + 3] & 0xffL);
    }

    /**
     * Parse 2 bytes from the byte array (starting at the offset) as unsigned 16-bit integer in big endian format.
     */
    public static int readUint16BE(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) |
                (bytes[offset + 1] & 0xff);
    }

    public static byte[] copyOf(byte[] in, int length) {
        byte[] out = new byte[length];
        System.arraycopy(in, 0, out, 0, Math.min(length, in.length));
        return out;
    }

    /**
     * Calculates RIPEMD160(SHA256(input)). This is used in Address calculations.
     */
    public static byte[] sha256hash160(byte[] input) {
        byte[] sha256 = Sha256Hash.hash(input);
        RIPEMD160Digest digest = new RIPEMD160Digest();
        digest.update(sha256, 0, sha256.length);
        byte[] out = new byte[20];
        digest.doFinal(out, 0);
        return out;
    }

    /**
     * The regular {@link BigInteger#toByteArray()} method isn't quite what we often need: it appends a
     * leading zero to indicate that the number is positive and may need padding.
     *
     * @param b        the integer to format into a byte array
     * @param numBytes the desired size of the resulting byte array
     * @return numBytes byte long array.
     */
    public static byte[] bigIntegerToBytes(BigInteger b, int numBytes) {
        if (b == null) {
            return null;
        }
        byte[] bytes = new byte[numBytes];
        byte[] biBytes = b.toByteArray();
        int start = (biBytes.length == numBytes + 1) ? 1 : 0;
        int length = Math.min(biBytes.length, numBytes);
        System.arraycopy(biBytes, start, bytes, numBytes - length, length);
        return bytes;
    }

    public static void uint32ToByteArrayBE(long val, byte[] out, int offset) {
        out[offset] = (byte) (0xFF & (val >> 24));
        out[offset + 1] = (byte) (0xFF & (val >> 16));
        out[offset + 2] = (byte) (0xFF & (val >> 8));
        out[offset + 3] = (byte) (0xFF & val);
    }

    public static void uint16ToByteArrayLE(short val, byte[] out, int offset) {
        out[offset] = (byte) (0xFF & val);
        out[offset + 1] = (byte) (0xFF & (val >> 8));
    }

    public static void int16ToByteArrayLE(short val, byte[] out, int offset) {
        out[offset] = (byte) (0xFF & val);
        out[offset + 1] = (byte) (0xFF & (val >> 8));
    }

    public static void uint32ToByteArrayLE(long val, byte[] out, int offset) {
        out[offset] = (byte) (0xFF & val);
        out[offset + 1] = (byte) (0xFF & (val >> 8));
        out[offset + 2] = (byte) (0xFF & (val >> 16));
        out[offset + 3] = (byte) (0xFF & (val >> 24));
    }

    public static void int32ToByteArrayLE(int val, byte[] out, int offset) {
        out[offset] = (byte) (0xFF & val);
        out[offset + 1] = (byte) (0xFF & (val >> 8));
        out[offset + 2] = (byte) (0xFF & (val >> 16));
        out[offset + 3] = (byte) (0xFF & (val >> 24));
    }

    public static void uint64ToByteArrayLE(long val, byte[] out, int offset) {
        out[offset] = (byte) (0xFF & val);
        out[offset + 1] = (byte) (0xFF & (val >> 8));
        out[offset + 2] = (byte) (0xFF & (val >> 16));
        out[offset + 3] = (byte) (0xFF & (val >> 24));
        out[offset + 4] = (byte) (0xFF & (val >> 32));
        out[offset + 5] = (byte) (0xFF & (val >> 40));
        out[offset + 6] = (byte) (0xFF & (val >> 48));
        out[offset + 7] = (byte) (0xFF & (val >> 56));
    }

    public static void int16ToByteStreamLE(short val, OutputStream stream) throws IOException {
        stream.write((byte) (0xFF & val));
        stream.write((byte) (0xFF & (val >> 8)));
    }

    public static void uint32ToByteStreamLE(long val, OutputStream stream) throws IOException {
        stream.write((int) (0xFF & val));
        stream.write((int) (0xFF & (val >> 8)));
        stream.write((int) (0xFF & (val >> 16)));
        stream.write((int) (0xFF & (val >> 24)));
    }

    public static void int64ToByteStreamLE(long val, OutputStream stream) throws IOException {
        stream.write((int) (0xFF & val));
        stream.write((int) (0xFF & (val >> 8)));
        stream.write((int) (0xFF & (val >> 16)));
        stream.write((int) (0xFF & (val >> 24)));
        stream.write((int) (0xFF & (val >> 32)));
        stream.write((int) (0xFF & (val >> 40)));
        stream.write((int) (0xFF & (val >> 48)));
        stream.write((int) (0xFF & (val >> 56)));
    }


    public static void uint64ToByteStreamLE(BigInteger val, OutputStream stream) throws IOException {
        byte[] bytes = val.toByteArray();
        if (bytes.length > MAGIC_8) {
            throw new RuntimeException("Input too large to encode into a uint64");
        }
        bytes = reverseBytes(bytes);
        stream.write(bytes);
        if (bytes.length < MAGIC_8) {
            for (int i = 0; i < MAGIC_8 - bytes.length; i++) {
                stream.write(0);
            }
        }
    }

    public static void doubleToByteStream(double val, OutputStream stream) throws IOException {
        stream.write(double2Bytes(val));
    }

    /**
     * 把double转为byte
     *
     * @param d
     * @return byte[]
     */
    public static byte[] double2Bytes(double d) {
        long value = Double.doubleToRawLongBits(d);
        byte[] byteRet = new byte[MAGIC_8];
        for (int i = 0; i < MAGIC_8; i++) {
            byteRet[i] = (byte) ((value >> 8 * i) & 0xff);
        }
        return byteRet;
    }

    /**
     * 把byte[]转double
     *
     * @param arr
     * @return double
     */
    public static double bytes2Double(byte[] arr) {
        long value = 0;
        for (int i = 0; i < MAGIC_8; i++) {
            value |= ((long) (arr[i] & 0xff)) << (MAGIC_8 * i);
        }
        return Double.longBitsToDouble(value);
    }

    public static <T> T checkNotNull(T t) {
        if (t == null) {
            throw new NullPointerException();
        }
        return t;
    }

    public static void checkState(boolean status) {
        if (status) {
            return;
        } else {
            throw new RuntimeException();
        }
    }

    public static void checkState(boolean status, Object... args) {
        if (status) {
            return;
        } else {
            String msg = null;
            if (args != null) {
                for (Object object : args) {
                    msg = (msg == null ? object.toString() : msg + object.toString());
                }
            }
            throw new RuntimeException("SYS_UNKOWN_EXCEPTION");
        }
    }

    public static String join(List<? extends Object> list) {
        if (list == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Object object : list) {
            if (sb.length() != 0) {
                sb.append(" ");
            }
            sb.append(object.toString());
        }
        return sb.toString();
    }

    public static short bytes2Short(byte[] b) {
        return (short) (((b[1] << 8) | b[0] & 0xff));
    }

    public static byte[] shortToBytes(short val) {
        byte[] bytes = new byte[2];
        bytes[1] = (byte) (0xFF & val >> 8);
        bytes[0] = (byte) (0xFF & val >> 0);
        return bytes;
    }

    public static byte[] int32ToBytes(int x) {
        byte[] bb = new byte[4];
        bb[3] = (byte) (0xFF & x >> 24);
        bb[2] = (byte) (0xFF & x >> 16);
        bb[1] = (byte) (0xFF & x >> 8);
        bb[0] = (byte) (0xFF & x >> 0);
        return bb;
    }

    public static long randomLong() {
        return (long) (Math.random() * Long.MAX_VALUE);
    }

    private static int sizeOfDouble(Double val) {
        byte[] bytes = Utils.double2Bytes(val);
        return VarInt.sizeOf(bytes.length) + bytes.length;
    }

    /**
     * @param data1
     * @param data2
     * @return data1 与 data2拼接的结果
     */
    public static byte[] addBytes(byte[] data1, byte[] data2) {
        byte[] data3 = new byte[data1.length + data2.length];
        System.arraycopy(data1, 0, data3, 0, data1.length);
        System.arraycopy(data2, 0, data3, data1.length, data2.length);
        return data3;
    }

    /**
     * 截取byte[],从index位置开始,包含此位置,注意数组都是从0开始的
     *
     * @param index
     * @param data
     * @return
     */
    public static byte[] subBytes(int index, byte[] data) {
        return Arrays.copyOfRange(data, index, data.length);
    }

    public static void main(String[] args) {
        byte[] aak = {'a', 'b', 'c'};
        byte[] bytes = subBytes(1, aak);
        System.out.println();
    }

    public static boolean equals(byte[] a, byte[] b) {
        return Arrays.equals(a, b);
    }

    /**
     * 根据用户公钥生成地址
     *
     * @param pub
     * @return
     */
    public static byte[] getAddress(byte[] pub) {
        byte[] hash160 = Utils.sha256hash160(pub);
        byte xor = 0x00;
        for (int i = 0; i < pub.length; i++) {
            xor ^= pub[i];
        }
        byte[] na = new byte[21];
        System.arraycopy(hash160, 0, na, 0, hash160.length);
        na[20] = xor;
        byte[] actualChecksum = Arrays.copyOfRange(Sha256Hash.hashTwice(na), 0, 4);
        na = Utils.addBytes(na,actualChecksum);
        return na;
    }

    /**
     * 显示地址
     *
     * @param address
     * @return
     */
    public static String showAddress(byte[] address) {
        return Base58.encode(address);
    }

    /**
     * 将地址反显为byte[]
     * @param address
     * @return
     * @throws Exception
     */
    public static byte[] deShowAddress(String address) throws Exception {
        return Base58.decode(address);
    }



}
