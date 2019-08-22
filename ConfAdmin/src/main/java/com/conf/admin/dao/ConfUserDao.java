package com.conf.admin.dao;

import com.conf.admin.core.model.ConfUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConfUserDao {

    public List<ConfUser> pageList(@Param("offset") int offset,
                                   @Param("pagesize") int pagesize,
                                   @Param("username") String username,
                                   @Param("permission") int permission);
    public int pageListCount(@Param("offset") int offset,
                             @Param("pagesize") int pagesize,
                             @Param("username") String username,
                             @Param("permission") int permission);

    public int add(ConfUser confUser);

    public int update(ConfUser confUser);

    public int delete(@Param("username") String username);

    public ConfUser load(@Param("username") String username);

}
