package jp.usagi.bank.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/** 店舗マスタ. */
@Entity
@Table(name = "BRANCH")
public class Branch {

    @Id
    @Column(name = "BRANCH_CODE", length = 3)
    private String code;

    @Column(name = "BRANCH_NAME", nullable = false, length = 40)
    private String name;

    @Column(name = "BRANCH_NAME_KANA", nullable = false, length = 40)
    private String nameKana;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNameKana() {
        return nameKana;
    }

    public void setNameKana(String nameKana) {
        this.nameKana = nameKana;
    }
}
