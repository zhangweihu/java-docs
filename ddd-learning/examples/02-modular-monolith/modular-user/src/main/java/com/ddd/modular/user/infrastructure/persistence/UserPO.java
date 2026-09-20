package com.ddd.modular.user.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

@Data
@TableName("t_user")
public class UserPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String email;
    private Instant createdAt;
}