package greenecomall.auth.support;

import greenecomall.auth.AuthErrors;
import greenecomall.common.domain.DomainException;

/**
 * Телефоны Кыргызстана в едином виде {@code +996XXXXXXXXX}: «+996 555 12-34-56», «0555123456»,
 * «555123456» и «996555123456» — один и тот же номер, один пользователь и один OTP-target.
 */
public final class Phones {

    private Phones() {
    }

    /** @throws DomainException {@link AuthErrors#PHONE_INVALID}, если это не номер KG */
    public static String normalize(String raw) {
        String phone = tryNormalize(raw);
        if (phone == null) {
            throw new DomainException(AuthErrors.PHONE_INVALID, "phone must be a Kyrgyzstan number: +996XXXXXXXXX");
        }
        return phone;
    }

    /** Как {@link #normalize}, но {@code null} вместо ошибки (данные из внешних систем). */
    public static String tryNormalize(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() == 10 && digits.startsWith("0")) {
            digits = "996" + digits.substring(1);
        } else if (digits.length() == 9) {
            digits = "996" + digits;
        }
        if (digits.length() != 12 || !digits.startsWith("996")) {
            return null;
        }
        return "+" + digits;
    }
}
