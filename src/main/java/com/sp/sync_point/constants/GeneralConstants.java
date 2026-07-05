package com.sp.sync_point.constants;

import java.math.BigDecimal;

public class GeneralConstants {

    public static final String OFFLINE_PAYMENT_APP_ACCESS_GROUP = "offline_pay_base_access_group";

    public static final String OFFLINE_PAYMENT_APP_ACCESS_ROLE = "offline_pay_base_access_role";

    public static final String ADMIN_ROLE = "admin_role";

    public static final int MAX_PIN_CHANGE_ATTEMPTS = 3;

    public static final int PIN_CHANGE_LOCK_MINUTES = 15;

    public static final String  INBOUND_VALUE = "INBOUND";

    public static final String  OUTBOUND_VALUE = "OUTBOUND";

    public static final int RANDOM_STRING_LENGTH = 12;

    public static final double MAX_DAILY_PAYMENT_LIMIT = 50000;

    public static final double MAX_RECEIPT_LIMIT = 100000;

    public static final double MAX_DAILY_TRANSACTIONS_LIMIT = 100000;

    public static final int MAX_LOGIN_ATTEMPTS = 5;

    public static final int WALLET_LOCKED_DURATION = 30;

    public static final BigDecimal MAX_TRANSACTION_AMOUNT = new BigDecimal("10000000.00");

    public static final BigDecimal ZERO = BigDecimal.ZERO;

    public static final String DATE_FORMAT = "yyyy-MM-dd";

    public static final String CURRENCY = "NGN";

    public static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    public static final int REF_LENGTH = 5;

    public static int MAX_ATTEMPTS = 5;

    public static final String SENDER_WALLET_ID = "SYNC_POINT_XXX";

    public static final String SENDER_NUMBER = "09111111111";

    public static final String SENDER_NAME = "SYNC_POINT_ADMIN";

    public static final String SYSTEM_PHONE_NUMBER = "09022233344";

    public static final String SYSTEM_NAME = "SYNC_POINT";

    public static final String INITIAL_TRANSACTION_DESCRIPTION = "INITIAL SYSTEM CAPITALIZATION FROM TREASURY FUNDS";

    public static final String FAILURE_REASONS = "INSUFFICIENT BALANCE";

    public static final BigDecimal SYSTEM_WALLET_AMOUNT = BigDecimal.valueOf(1000000000);

    public static final BigDecimal FUND_AMOUNT = BigDecimal.valueOf(1000000);

    public static final Long SYSTEM_WALLET_ID = 1L;

    public static final BigDecimal OFFLINE_LIMIT = BigDecimal.valueOf(1000000);

    public static final long SESSION_TTL_DAYS = 60;


    private GeneralConstants() {
        throw new UnsupportedOperationException("General constant class and cannot be instantiated");
    }
}