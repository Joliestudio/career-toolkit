package com.Jolie.career_toolkit.company;

import jakarta.persistence.*;

@Entity
@Table(name = "industries")
public class Industry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "name_zh", nullable = false, length = 60)
    private String nameZh;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(nullable = false)
    private Boolean active;

    protected Industry() {}

    public Industry(String code, String nameZh, Integer sortOrder) {
        this.code = code;
        this.nameZh = nameZh;
        this.sortOrder = sortOrder;
        this.active = true;
    }

    public Short getId() { return id; }
    public String getCode() { return code; }
    public String getNameZh() { return nameZh; }
    public Integer getSortOrder() { return sortOrder; }
    public Boolean getActive() { return active; }

    public void setNameZh(String nameZh) { this.nameZh = nameZh; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public void setActive(Boolean active) { this.active = active; }
}
