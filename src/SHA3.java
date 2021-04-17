public class SHA3 {
    //SHA-3 implementation converted from C to Java from https://github.com/mjosaarinen/tiny_sha3
    public static final int KECCAKF_ROUNDS 24

    // state context
    public static byte[] b = new byte[200];
    int pt, rsiz, mdlen;

    public static long ROTL64(long x, long y) {
        return (((x) << (y)) | ((x) >>> (64 - (y))));
    }

    void sha3_keccakf(long st[25])
    {
        // constants
        long[/*24*/] keccakf_rndc = {
            0x0000000000000001L, 0x0000000000008082L, 0x800000000000808aL,
                    0x8000000080008000L, 0x000000000000808bL, 0x0000000080000001L,
                    0x8000000080008081L, 0x8000000000008009L, 0x000000000000008aL,
                    0x0000000000000088L, 0x0000000080008009L, 0x000000008000000aL,
                    0x000000008000808bL, 0x800000000000008bL, 0x8000000000008089L,
                    0x8000000000008003L, 0x8000000000008002L, 0x8000000000000080L,
                    0x000000000000800aL, 0x800000008000000aL, 0x8000000080008081L,
                    0x8000000000008080L, 0x0000000080000001L, 0x8000000080008008L
        };
        int[/*24*/] keccakf_rotc = {
            1,  3,  6,  10, 15, 21, 28, 36, 45, 55, 2,  14,
                    27, 41, 56, 8,  25, 43, 62, 18, 39, 61, 20, 44
        };
        int[/*24*/] keccakf_piln = {
            10, 7,  11, 17, 18, 3, 5,  16, 8,  21, 24, 4,
                    15, 23, 19, 13, 12, 2, 20, 14, 22, 9,  6,  1
        };

        // variables
        int i, j, r;
        long t;
        long[/*5*/] bc;

        #if __BYTE_ORDER__ != __ORDER_LITTLE_ENDIAN__
            uint8_t *v;

            // endianess conversion. this is redundant on little-endian targets
            for (i = 0; i < 25; i++) {
                v = (uint8_t *) &st[i];
                st[i] = ((uint64_t) v[0])     | (((uint64_t) v[1]) << 8) |
                        (((uint64_t) v[2]) << 16) | (((uint64_t) v[3]) << 24) |
                        (((uint64_t) v[4]) << 32) | (((uint64_t) v[5]) << 40) |
                        (((uint64_t) v[6]) << 48) | (((uint64_t) v[7]) << 56);
            }
        #endif

        // actual iteration
        for (r = 0; r < KECCAKF_ROUNDS; r++) {

            // Theta
            for (i = 0; i < 5; i++)
                bc[i] = st[i] ^ st[i + 5] ^ st[i + 10] ^ st[i + 15] ^ st[i + 20];

            for (i = 0; i < 5; i++) {
                t = bc[(i + 4) % 5] ^ ROTL64(bc[(i + 1) % 5], 1);
                for (j = 0; j < 25; j += 5)
                    st[j + i] ^= t;
            }

            // Rho Pi
            t = st[1];
            for (i = 0; i < 24; i++) {
                j = keccakf_piln[i];
                bc[0] = st[j];
                st[j] = ROTL64(t, keccakf_rotc[i]);
                t = bc[0];
            }

            //  Chi
            for (j = 0; j < 25; j += 5) {
                for (i = 0; i < 5; i++)
                    bc[i] = st[j + i];
                for (i = 0; i < 5; i++)
                    st[j + i] ^= (~bc[(i + 1) % 5]) & bc[(i + 2) % 5];
            }

            //  Iota
            st[0] ^= keccakf_rndc[r];
        }

        #if __BYTE_ORDER__ != __ORDER_LITTLE_ENDIAN__
            // endianess conversion. this is redundant on little-endian targets
            for (i = 0; i < 25; i++) {
                v = (uint8_t *) &st[i];
                t = st[i];
                v[0] = t & 0xFF;
                v[1] = (t >>> 8) & 0xFF;
                v[2] = (t >>> 16) & 0xFF;
                v[3] = (t >>> 24) & 0xFF;
                v[4] = (t >>> 32) & 0xFF;
                v[5] = (t >>> 40) & 0xFF;
                v[6] = (t >>> 48) & 0xFF;
                v[7] = (t >>> 56) & 0xFF;
            }
        #endif
    }

    // Initialize the context for SHA3

    void sha3_init(int mdlen)
    {
        int i;

        for (i = 0; i < 200; i++)
            b[i] = 0;
        this.mdlen = mdlen;
        rsiz = 200 - 2 * mdlen;
        pt = 0;
    }

    // update state with more data

    int sha3_update(sha3_ctx_t *c, const void *data, size_t len)
    {
        size_t i;
        int j;

        j = c->pt;
        for (i = 0; i < len; i++) {
            c->st.b[j++] ^= ((const uint8_t *) data)[i];
            if (j >= c->rsiz) {
                sha3_keccakf(c->st.q);
                j = 0;
            }
        }
        c->pt = j;

        return 1;
    }

    // finalize and output a hash

    int sha3_final(void *md, sha3_ctx_t *c)
    {
        int i;

        c->st.b[c->pt] ^= 0x06;
        c->st.b[c->rsiz - 1] ^= 0x80;
        sha3_keccakf(c->st.q);

        for (i = 0; i < c->mdlen; i++) {
            ((uint8_t *) md)[i] = c->st.b[i];
        }

        return 1;
    }

    // compute a SHA-3 hash (md) of given byte length from "in"

    void *sha3(const void *in, size_t inlen, void *md, int mdlen)
    {
        sha3_ctx_t sha3;

        sha3_init(&sha3, mdlen);
        sha3_update(&sha3, in, inlen);
        sha3_final(md, &sha3);

        return md;
    }

    // SHAKE128 and SHAKE256 extensible-output functionality

    void shake_xof(sha3_ctx_t *c)
    {
        c->st.b[c->pt] ^= 0x1F;
        c->st.b[c->rsiz - 1] ^= 0x80;
        sha3_keccakf(c->st.q);
        c->pt = 0;
    }

    void shake_out(sha3_ctx_t *c, void *out, size_t len)
    {
        size_t i;
        int j;

        j = c->pt;
        for (i = 0; i < len; i++) {
            if (j >= c->rsiz) {
                sha3_keccakf(c->st.q);
                j = 0;
            }
            ((uint8_t *) out)[i] = c->st.b[j++];
        }
        c->pt = j;
    }



    public static String right_encode(long x) {
        byte n;
        if (x == 0) {
            n = 1;
        } else {
            double nGreaterThan = Math.log10(x) / Math.log10(2) / 8;
            if (nGreaterThan == (int) nGreaterThan) {
                n = (byte) (nGreaterThan + 1);
            } else {
                n = (byte) Math.ceil(nGreaterThan);
            }
        }

        byte[] xArr = new byte[n];

        for (int i = 0; i < n; i++) {
            long mask = 0xFF << (8 * (n - i - 1));
            byte x_i = (byte) ((x & mask) >>> (8 * (n - i - 1)));
            xArr[i] = x_i;
        }

        String[] oArr = new String[n+1];

        for (int i = 0; i < n; i++) {
            oArr[i] = enc8(xArr[i]);
        }

        oArr[n] = enc8(n);
        String O = "";
        for (int i = 0; i < n+1; i++) {
            O = O.concat(oArr[i]);
        }

        return O;

    }

    public static String left_encode(long x) {
        byte n;
        if (x == 0) {
            n = 1;
        } else {
            double nGreaterThan = Math.log10(x) / Math.log10(2) / 8;
            if (nGreaterThan == (int) nGreaterThan) {
                n = (byte) (nGreaterThan + 1);
            } else {
                n = (byte) Math.ceil(nGreaterThan);
            }
        }

        byte[] xArr = new byte[n];

        for (int i = 0; i < n; i++) {
            long mask = 0xFF << (8 * (n - i - 1));
            byte x_i = (byte) ((x & mask) >>> (8 * (n - i - 1)));
            xArr[i] = x_i;
        }

        String[] oArr = new String[n+1];

        oArr[0] = enc8(n);
        for (int i = 0; i < n; i++) {
            oArr[i+1] = enc8(xArr[i]);
        }

        String O = "";
        for (int i = 0; i < n+1; i++) {
            O = O.concat(oArr[i]);
        }

        return O;
    }

    public static String encode_string(String S) {
        return left_encode(S.length()).concat(S);
    }

    public static String bytepad(String X, long w) {
        String z = left_encode(w).concat(X);
        while (z.length() % 8 != 0) {
            z = z.concat("0");
        }

        while ((z.length() / 8) % w != 0) {
            z = z.concat("00000000");
        }

        return z;
    }

    private static String enc8(byte b) {
        StringBuilder ret = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (b != 0) {
                ret.append(Integer.toString(Math.abs(b % 2)));
                b >>>= 1;
            } else {
                ret.append("0");
            }
        }

        return ret.toString();
    }
}
