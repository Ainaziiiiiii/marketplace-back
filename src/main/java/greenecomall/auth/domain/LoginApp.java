package greenecomall.auth.domain;

/**
 * Приложение, из которого входят по SMS. Покупатель и магазин: новый номер — новый аккаунт, код из 6 цифр.
 * Курьер: только заведённые курьеры (роль COURIER), код из 4 цифр.
 */
public enum LoginApp {
    CLIENT,
    SHOP,
    COURIER
}
