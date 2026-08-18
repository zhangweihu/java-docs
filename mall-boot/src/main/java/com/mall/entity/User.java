package com.mall.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，对应表 t_user。
 * @TableLogic 逻辑删除：删除为 update deleted=1，查询自动过滤 deleted=0
 */
@Data
@TableName("t_user")
public class User {

    /** 主键：数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 密文，序列化给前端时忽略，防止泄露 */
    @JsonIgnore
    private String password;

    private String nickname;

    private String phone;

    /** 0-正常 1-禁用 */
    private Integer status;

    /** 逻辑删除标记：0-未删除 1-已删除 */
    @TableLogic
    @JsonIgnore
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
