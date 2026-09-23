package jp.usagi.bank.xml;

import java.text.SimpleDateFormat;
import java.util.Date;

import javax.xml.bind.annotation.adapters.XmlAdapter;

/** yyyy-MM-dd 形式. SimpleDateFormat はスレッドセーフでないため都度生成. */
public class DateAdapter extends XmlAdapter<String, Date> {

    @Override
    public Date unmarshal(String v) throws Exception {
        return v == null ? null : new SimpleDateFormat("yyyy-MM-dd").parse(v);
    }

    @Override
    public String marshal(Date v) {
        return v == null ? null : new SimpleDateFormat("yyyy-MM-dd").format(v);
    }
}
