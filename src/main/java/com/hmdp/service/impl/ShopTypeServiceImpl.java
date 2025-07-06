package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {


    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 商铺类型
     * @return
     */
    public Result queryOrderByAsc() {
        String key = "SHOP-TYPE";
        //在redis中查商铺类型信息
        String shoptypejson = stringRedisTemplate.opsForValue().get(key);
        //查询是否存在
        if (StrUtil.isNotBlank(shoptypejson)){
            //如果存在，则直接返回
            List<ShopType> shopTypes = JSONUtil.toList(shoptypejson, ShopType.class);
            return Result.ok(shopTypes);
        }
        //在数据库中查询
        List<ShopType> shopTypes = query().orderByAsc("sort").list();

        if (shopTypes == null){
            //不存在，则返回
            return Result.fail("商铺类型不存在");
        }
        //存在，则返回redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shopTypes));
        return Result.ok(shopTypes);
    }
}
