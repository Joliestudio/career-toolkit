package com.Jolie.career_toolkit.answers;

import jakarta.persistence.*;

@Entity
@Table(name = "question_types")
public class QuestionType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Short id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "name_zh", nullable = false, length = 80)
    private String nameZh;

    /** 這一題該怎麼答的提示。準備的時候最需要的其實是這個。 */
    @Column(columnDefinition = "TEXT")
    private String hint;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(nullable = false)
    private Boolean active;

    protected QuestionType() {}

    public Short getId() { return id; }
    public String getCode() { return code; }
    public String getNameZh() { return nameZh; }
    public String getHint() { return hint; }
    public Integer getSortOrder() { return sortOrder; }
    public Boolean getActive() { return active; }
}
