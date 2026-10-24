package com.notificationreader;

import org.junit.Test;
import static org.junit.Assert.*;

public class NotificationParserTest {
    @Test public void parsesYapeExample() {
        NotificationParser.Payment p = NotificationParser.parse("YAPE", "Recibiste un yapeo", "QR DE JOHN OLIV te envió Bs. 1.00");
        assertNotNull(p); assertEquals("JOHN OLIV", p.user); assertEquals("1", p.amount.toPlainString());
    }
    @Test public void parsesBcpExample() {
        NotificationParser.Payment p = NotificationParser.parse("BCP", "Deposito en cuenta", "Recibiste un pago QR por Bs. 1950.00 de ANEZ MERLIN RANDY");
        assertNotNull(p); assertEquals("ANEZ MERLIN RANDY", p.user); assertEquals("1950", p.amount.toPlainString());
    }
    @Test public void parsesAltokeNotificationExample() {
        NotificationParser.Payment p = NotificationParser.parse("ALTOKE", "Recibiste un pago", "JOHN O te abonó Bs. 1.00");
        assertNotNull(p); assertEquals("JOHN O", p.user); assertEquals("1", p.amount.toPlainString());
    }
    @Test public void ignoresAltokeTitleWithoutPaymentBody() {
        assertNull(NotificationParser.parse("ALTOKE", "Recibiste un pago", ""));
    }
    @Test public void ignoresUnrecognizedMessage() {
        assertNull(NotificationParser.parse("YAPE", "Promoción", "Conoce nuestros beneficios"));
    }
}
