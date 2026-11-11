package jp.usagi.bank.xml;

import java.text.SimpleDateFormat;
import java.util.Date;

import jakarta.xml.bind.annotation.adapters.XmlAdapter;

public class DateTimeAdapter extends XmlAdapter<String, Date> {

    private static final String PATTERN = "yyyy-MM-dd'T'HH:mm:ssXXX";

    @Override
    public Date unmarshal(String v) throws Exception {
        return v == null ? null : new SimpleDateFormat(PATTERN).parse(v);
    }

    @Override
    public String marshal(Date v) {
        return v == null ? null : new SimpleDateFormat(PATTERN).format(v);
    }
}
