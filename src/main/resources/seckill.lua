-- 1. 参数列表
-- 1.1. 优惠券库存key
local stockKey = KEYS[1]
-- 1.2. 订单key（已购用户集合）
local orderKey = KEYS[2]
-- 1.3. 消息队列key
local streamKey = KEYS[3]

-- 1.4. 用户id
local userId = ARGV[1]
-- 1.5. 优惠券id
local voucherId = ARGV[2]
-- 1.6. 订单id（由Java端提前生成并传进来）
local orderId = ARGV[3]

-- 2. 执行脚本
-- 2.1. 判断库存是否充足
local stock = redis.call('get', stockKey)
if (not stock or tonumber(stock) <= 0) then
    -- 如果库存不存在，或者库存小于等于0，返回1代表库存不足
    return 1
end

-- 2.2. 判断用户是否已经下单过（一人一单）
if (redis.call('sismember', orderKey, userId) == 1) then
    -- 如果当前用户ID已在Set集合中，返回2代表重复下单
    return 2
end

-- 2.3. 扣减库存（将库存值减1）
redis.call('incrby', stockKey, -1)

-- 2.4. 将当前用户保存到已下单集合中
redis.call('sadd', orderKey, userId)

-- 2.5. 新加：发送消息到队列中：XADD stream.orders * userId {userId} voucherId {voucherId} id {orderId}
redis.call('xadd', streamKey, '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)

-- 3. 返回0代表成功抢到资格并且成功放入队列
return 0
