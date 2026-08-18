package com.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.entity.User;

/**
 * 用户 Mapper：继承 BaseMapper 即拥有单表 CRUD。
 * 复杂 SQL 可在此声明方法并在 resources/mapper 下写 XML。
 */
public interface UserMapper extends BaseMapper<User> {
}
