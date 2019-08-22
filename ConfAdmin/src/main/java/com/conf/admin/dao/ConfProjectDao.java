package com.conf.admin.dao;

import com.conf.admin.core.model.ConfProject;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConfProjectDao {

    public List<ConfProject> findAll();

    public int save(ConfProject confProject);

    public int update(ConfProject confProject);

    public int delete(@Param("appname") String appname);

    public ConfProject load(@Param("appname") String appname);

}