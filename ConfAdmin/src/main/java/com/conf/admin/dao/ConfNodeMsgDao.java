package com.conf.admin.dao;

import com.conf.admin.core.model.ConfNodeMsg;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ConfNodeMsgDao {

	public void add(ConfNodeMsg confNode);

	public List<ConfNodeMsg> findMsg(@Param("readedMsgIds") List<Integer> readedMsgIds);

	public int cleanMessage(@Param("messageTimeout") int messageTimeout);

}
