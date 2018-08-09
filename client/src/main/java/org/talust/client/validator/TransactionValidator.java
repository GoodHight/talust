/*
 * MIT License
 *
 * Copyright (c) 2017-2018 talust.org talust.io
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */

package org.talust.client.validator;

import lombok.extern.slf4j.Slf4j;
import org.talust.account.AccountStatus;
import org.talust.account.AccountType;
import org.talust.common.crypto.*;
import org.talust.common.model.*;
import org.talust.core.transaction.Transaction;
import org.talust.network.MessageValidator;
import org.talust.network.netty.queue.MessageQueueHolder;
import org.talust.storage.BlockStorage;
import org.talust.storage.ChainStateStorage;
import org.talust.common.tools.*;

/**
 * 交易数据校验,是指的一条一条的交易数据
 */
@Slf4j
public class TransactionValidator implements MessageValidator {
    private MessageQueueHolder mqHolder = MessageQueueHolder.get();
    private BlockStorage blockStorage = BlockStorage.get();
    private ChainStateStorage stateStorage = ChainStateStorage.get();
    private double nearZero = 0.0000000000001;
    @Override
    public boolean check(MessageChannel message) {//对每一条交易数据进行验证
        Message msg = message.getMessage();
        if (msg.getSigner() != null && msg.getSigner().length > 0) {
            boolean signOk = mqHolder.checkSign(msg);
            if (!signOk) {//签名不正确
                return false;
            }
        }

        Transaction transaction = SerializationUtil.deserializer(msg.getContent(), Transaction.class);
        if (checkReasonable(msg.getSigner(), transaction)) {//签名者就是交易的发起者
            return true;
        }
        return false;
    }

    /**
     * 检测交易数据的合理性
     *
     * @param signerPub
     * @param transaction
     * @return
     */
    private boolean checkReasonable(byte[] signerPub, Transaction transaction) {
        int tranType = transaction.getTranType();
        if (tranType == TranType.ACCOUNT.getType()) {//是帐户下发的交易类型
            return checkAccountPub(transaction);
        } else if (tranType == TranType.TRANSFER.getType()) {//是转账的交易类型
            return checkTransfer(signerPub, transaction);
        } else if (tranType == TranType.COIN_BASE.getType()) {//是挖矿的数据
            return checkCoinBase(transaction);
        }
        //TODO 业务的交易类型以及储蓄的交易类型后续需要实现
        return false;
    }

    /**
     * 检测账户下发
     *
     * @param transaction
     * @return
     */
    private boolean checkAccountPub(Transaction transaction) {
        boolean isOk = false;
        Account account = SerializationUtil.deserializer(transaction.getDatas(), Account.class);
        Integer accType = account.getAccType();
        if (accType != null) {
            if (accType == AccountType.ROOT.getType() || accType == AccountType.TALUST.getType() || accType == AccountType.MINING.getType()
                    || accType == AccountType.USER.getType() || accType == AccountType.ADMIN.getType() || accType == AccountType.HR.getType()) {
                byte[] parentPub = account.getParentPub();
                if (parentPub != null) {
                    Sha256Hash hash = Sha256Hash.of(account.getPublicKey());
                    boolean verify = ECKey.verify(hash.getBytes(), account.getParentSign(), account.getParentPub());
                    if (verify) {
                        byte[] parentAddr = Utils.getAddress(parentPub);//父级节点地址
                        byte[] parentAddrKey = Utils.addBytes(Constant.ACC_PREFIX, parentAddr);
                        byte[] parentBytes = blockStorage.get(parentAddrKey);
                        if (parentBytes == null) {
                            parentBytes = CacheManager.get().get(Hex.encode(parentAddrKey));
                        }
                        if (parentBytes == null) {//有可能是根用户
                            if (Utils.equals(account.getPublicKey(), Hex.decode(CacheManager.get().get("ROOT_PK")))) {//根证书验证正确
                                isOk = true;
                            }
                        } else {
                            if (accType == AccountType.TALUST.getType()) {//是运营方类型
                                if (Utils.equals(account.getParentPub(), Hex.decode(CacheManager.get().get("ROOT_PK")))) {//运营方的父级证书只能是根
                                    isOk = true;
                                }
                            } else {
                                //其余类型,则检测父类型是运营方类型即可
                                Account tp = SerializationUtil.deserializer(parentBytes, Account.class);
                                if (tp != null && tp.getStatus() == AccountStatus.ENABLE.getType() && tp.getAccType().intValue() == AccountType.TALUST.getType()) {
                                    isOk = true;
                                }
                            }
                        }
                        if (isOk) {
                            //父级节点地址
                            byte[] slfAddr = account.getAddress();
                            byte[] slfAddrKey = Utils.addBytes(Constant.ACC_PREFIX, slfAddr);
                            CacheManager.get().put(Hex.encode(slfAddrKey), transaction.getDatas(), 5);
                        }
                    }
                }
            }
        }
        return isOk;
    }

    /**
     * 检测挖矿所得
     *
     * @param transaction
     * @return
     */
    private boolean checkCoinBase(Transaction transaction) {
//        List<TransactionOut> outs = transaction.getOuts();
//        if (outs != null) {
//            boolean result = true;
//            for (TransactionOut out : outs) {
//                Integer coinBaseType = out.getCoinBaseType();
//                if (coinBaseType != null) {
//                    //@TODO 后续考虑验证接收地址是否与height匹配
//                    //是挖矿所得
//                    if (coinBaseType.intValue() == CoinBaseType.MINING.getType()) {
//                        byte[] address = out.getAddress();
//                        //将挖矿收益地址存于缓存中
//                        String cacheKey = new String(Constant.MINING_ADDRESS);
//                        List<String> madress = CacheManager.get().get(cacheKey);
//                        if (madress.contains(Utils.showAddress(address))) {
//                            int currentBlockHeight = CacheManager.get().getCurrentBlockHeight();
//                            double baseCoin = MiningRule.getBaseCoin(currentBlockHeight + 1);
//                            double amount = out.getAmount();
//                            //说明本次挖矿所得数量没有问题
//                            if (Math.abs(baseCoin - amount) > nearZero) {
//                                result = false;
//                                break;
//                            }
//                        }
//                    } else if (coinBaseType.intValue() == CoinBaseType.DEPOSITION.getType()) {//是储蓄所得
//                        byte[] address = out.getAddress();
//                        //将挖矿收益地址存于缓存中
//                        String cacheKey = new String(Constant.MINING_ADDRESS);
//                        List<String> madress = CacheManager.get().get(cacheKey);
//                        //储蓄地址中含有挖矿节点地址,则说明当前还没有其他的储蓄用户,则挖矿全部奖励给本次的挖矿节点帐户
//                        if (madress.contains(Utils.showAddress(address))) {
//                            int currentBlockHeight = CacheManager.get().getCurrentBlockHeight();
//                            double depositCoin = MiningRule.getDepositCoin(currentBlockHeight + 1);
//                            double amount = out.getAmount();
//                            if (Math.abs(depositCoin - amount) > nearZero) {//说明本次挖矿所得数量没有问题
//                                result = false;
//                                break;
//                            }
//                        } else {//含有储蓄地址 @TODO,实现时需要判断数量的合理性,即本次应该获得的总的挖矿奖励以及当前目标地址的储蓄占有多少
//                            //TODO   关于储蓄地址的获取与存储  是一个问题 ， 占比与金额总数 存储是个问题 ， 尝试用 挖矿地址 +  MAP<ADDR, AMOUNT > 去存储总数等
//
//                        }
//                    }
//                }
//            }
//            return result;
//        }
        return false;
    }

    /**
     * 检测转账交易
     *
     * @param signerPub
     * @param transaction
     * @return
     */
    private boolean checkTransfer(byte[] signerPub, Transaction transaction) {
//        List<TransactionIn> ins = transaction.getIns();
//        if (ins != null && ins.size() > 0) {
//            List<TransactionOut> inOuts = new ArrayList<>();
//            //当前用户的账户总金额
//            BigDecimal userAmount = new BigDecimal(0);
//            //签名者的地址
//            byte[] signAddr = Base58.encode(Utils.sha256hash160(signerPub)).getBytes();
//            for (TransactionIn in : ins) {
//                long tranNumber = in.getTranNumber();
//                int item = in.getItem();
//                boolean disable = TransactionCache.get().isDisable(transaction.getTranNumber(), item);
//                //说明当前的票子已经有可能被使用过了,主要是为了防止双花
//                if (disable) {
//                    return false;
//                }
//                String tid = tranNumber + "-" + item;
//                byte[] transactionOut = stateStorage.get(tid.getBytes());
//                TransactionOut out = SerializationUtil.deserializer(transactionOut, TransactionOut.class);
//                if (out != null) {
//                    byte[] address = out.getAddress();
//                    //是当前签名者的余钱
//                    if (Utils.equals(address, signAddr)) {
//                        inOuts.add(out);
//                        userAmount.add(new BigDecimal(out.getAmount()));
//                    }
//                }
//            }
//            //说明当前帐户有票子
//            if (inOuts.size() > 0) {
//                List<TransactionOut> outs = transaction.getOuts();
//                if (outs != null && outs.size() > 0) {
//                    //转出的总金额
//                    BigDecimal outAmount = new BigDecimal(0);
//                    for (TransactionOut out : outs) {
//                        outAmount.add(new BigDecimal(out.getAmount()));
//                    }
//                    //说明本次转账成立,当前用户的余额减去目标帐户
//                    double ye = userAmount.subtract(outAmount).doubleValue();
//                    //将本次交易的输入项设置为不可用
//                    if (ye >= 0) {
//                        for (TransactionIn in : transaction.getIns()) {
//                            long tranNumber = in.getTranNumber();
//                            int item = in.getItem();
//                            TransactionCache.get().disableOut(tranNumber, item);
//                        }
//                        //TODO if transfer success ,we need  got the addrs in accountStorage
//                        // TODO  save the transfer in an local cache
//                        return true;
//                    }
//                }
//            }
//        }
        return true;
    }

}
