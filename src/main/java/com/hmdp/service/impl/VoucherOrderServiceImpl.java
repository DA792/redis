package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisldWoker;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisldWoker redisldWoker;
    /**
     * 秒杀卷
     * @param voucherId
     * @return
     */
    public Result seckillVoucher(Long voucherId) {
        //根据id查询优惠劵信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now()) ){
            return Result.fail("秒杀未开始");
        }
        //判断秒杀是否结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now()) ){
            return Result.fail("秒杀已经结束");
        }
        //判断库存是否充足
        if (voucher.getStock() < 1){
            return Result.fail("库存不足");
        }
        //扣减库存

       boolean success =  seckillVoucherService.update().setSql("stock = stock -1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)  // 库存大于0才能扣减
                .update();
        if (! success){
            return Result.fail("库存不足");
        }
        //6.一人一单
        Long userId = UserHolder.getUser().getId();
        //6.1查询订单
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        //6.2判断是否存在

        //存在
        if (count > 0 ){
            return Result.fail("重复下单");
        }
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单id
        long orderId = redisldWoker.nextId("order");
        voucherOrder.setId(orderId);
        //用户id

        Long UserId = UserHolder.getUser().getId();
        voucherOrder.setUserId(UserId);
        //优惠券id
        voucherOrder.setVoucherId(voucherId);
        //返回订单
        save(voucherOrder);
        //返回订单id
        return Result.ok(orderId);
    }
}
