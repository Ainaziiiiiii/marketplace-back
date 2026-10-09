package greenecomall.auth.web.dto;

/**
 * @param phone         номер в формате +996XXXXXXXXX — показать «Код отправлен на …»
 * @param codeLength    сколько цифр ждать (покупатель 6, курьер 4)
 * @param resendAfterSec через сколько секунд можно запросить новый код
 * @param expiresInSec  сколько живёт код
 */
public record OtpChallengeResponse(String phone, int codeLength, long resendAfterSec, long expiresInSec) {
}
