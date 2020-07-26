package ke.axle.chassis.utils;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public abstract class UuidProvider {

    public static UUID createUuid(){
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return new UUID(random.nextLong(), random.nextLong());
    }

    public static UUID fromString(String str) {
        if (str == null)
            return null;

        if (str.length() != 36) {
            if (!str.contains("-") && str.length() == 32) {
                str = str.substring(0, 8) + "-"
                        + str.substring(8, 12) + "-"
                        + str.substring(12, 16) + "-"
                        + str.substring(16, 20) + "-"
                        + str.substring(20);
            } else {
                throw new IllegalArgumentException(str);
            }
        }
        long m = 0, x = 0;
        for (int i = 0; i < 36; i++) {
            int c = str.charAt(i);
            switch (i) {
                case 18:
                    m = x;
                    x = 0;
                    // fall through
                case 8:
                case 13:
                case 23:
                    if (c != '-') {
                        throw new IllegalArgumentException(str);
                    }
                    break;
                default:
                    if (c >= '0' && c <= '9') {
                        x = (x << 4) | (c - '0');
                    } else if (c >= 'a' && c <= 'f') {
                        x = (x << 4) | (c - 'a' + 0xa);
                    } else if (c >= 'A' && c <= 'F') {
                        x = (x << 4) | (c - 'A' + 0xa);
                    } else {
                        throw new IllegalArgumentException(str);
                    }
            }
        }
        return new UUID(m, x);
    }

}
