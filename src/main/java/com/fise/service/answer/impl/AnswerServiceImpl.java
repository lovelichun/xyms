package com.fise.service.answer.impl;

import java.util.ArrayList;
import java.util.List;

import org.aspectj.asm.internal.Relationship;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fise.base.Page;
import com.fise.base.Response;
import com.fise.dao.AgreeMapper;
import com.fise.dao.AnswerMapper;
import com.fise.dao.IMRelationShipMapper;
import com.fise.dao.IMUserMapper;
import com.fise.dao.ProblemsMapper;
import com.fise.framework.redis.RedisManager;
import com.fise.model.entity.Agree;
import com.fise.model.entity.AgreeExample;
import com.fise.model.entity.Answer;
import com.fise.model.entity.AnswerExample;
import com.fise.model.entity.IMUser;
import com.fise.model.entity.AnswerExample.Criteria;
import com.fise.model.entity.Problems;
import com.fise.model.result.AnswerResult;
import com.fise.service.answer.IAnswerService;
import com.fise.utils.Constants;
import com.fise.utils.DateUtil;
import redis.clients.jedis.Jedis;

@Service
public class AnswerServiceImpl implements IAnswerService{
    
    @Autowired
    AnswerMapper answerDao;
    
    @Autowired
    ProblemsMapper problemDao;
    
    @Autowired
    IMUserMapper userDao;
    
    @Autowired
    AgreeMapper agreeDao;
    
    @Autowired
    IMRelationShipMapper relationShipDao;
    
    @Override
    public Response insertAnswer(Answer record) {
        Response res = new Response();
        record.setUpdated(DateUtil.getLinuxTimeStamp());
        record.setCreated(DateUtil.getLinuxTimeStamp());
        
        answerDao.insertSelective(record);
        
        Problems problems = problemDao.selectByPrimaryKey(record.getProblemId());
        problems.setAnswerNum(problems.getAnswerNum()+1);
        problems.setUpdated(DateUtil.getLinuxTimeStamp());
        problemDao.updateByPrimaryKey(problems);
        
        AnswerExample example = new AnswerExample();
        Criteria criteria = example.createCriteria();
        criteria.andUserIdEqualTo(record.getUserId());
        example.setOrderByClause("created desc");
        
        List<Answer> list=answerDao.selectByExample(example);
        Answer answer=list.get(0);
        
        Jedis jedis=null;
        try {
            jedis=RedisManager.getInstance().getResource(Constants.REDIS_POOL_NAME_MEMBER);
            String key=answer.getId()+"agree";
            String value=answer.getAgreeNum()+"";
            jedis.set(key, value);
            
            /*jedis.setex(key, Constants.ACCESS_TOKEN_EXPIRE_SECONDS, value);*/
            
            key=answer.getId()+"comment";
            value=answer.getCommentNum()+"";
            jedis.set(key, value);
            
            /*jedis.setex(key, Constants.ACCESS_TOKEN_EXPIRE_SECONDS, value);*/
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            RedisManager.getInstance().returnResource(Constants.REDIS_POOL_NAME_MEMBER, jedis);
        }
        return res.success();
    }

    @Override
    public Response queryMyAnswer(Page<Answer> page) {
        Response res = new Response();
        
        AnswerExample example = new AnswerExample();
        Criteria criteria = example.createCriteria();
        criteria.andStatusEqualTo(1);
        criteria.andUserIdEqualTo(page.getParam().getUserId());
        
        example.setOrderByClause("created desc");
        
        List<Answer> list=answerDao.selectBypage(example, page);
        
        //查询用户昵称和头像
        IMUser user=userDao.selectByPrimaryKey(page.getParam().getUserId());
        
        Page<AnswerResult> reslut = new Page<AnswerResult>();
        
        reslut.setPageNo(page.getPageNo());
        reslut.setPageSize(page.getPageSize());
        reslut.setTotalCount(page.getTotalCount());
        reslut.setTotalPageCount(page.getTotalPageCount());
        
        Jedis jedis=null;
        List<AnswerResult> listResult=new ArrayList<>();
        
        for(Answer answer:list){
            try {
                jedis=RedisManager.getInstance().getResource(Constants.REDIS_POOL_NAME_MEMBER);
                AnswerResult aResult = new AnswerResult();
                
                String key=answer.getId()+"agree";
                String value=jedis.get(key);            
                aResult.setAddAgreeCount(answer.getAgreeNum()-Integer.valueOf(value));
                
                key=answer.getId()+"comment";
                value=jedis.get(key);
                aResult.setAddCommentCount(answer.getCommentNum()-Integer.valueOf(value));
                
                setResult(aResult,answer,user);
                
                listResult.add(aResult);
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }finally {
                RedisManager.getInstance().returnResource(Constants.REDIS_POOL_NAME_MEMBER, jedis);
            } 
        }
              
        reslut.setResult(listResult);
       
        return res.success(reslut);
     
    }

    @Override
    public Response queryAnswerById(Page<Answer> page,String order) {
        Response res = new Response();
        
        AnswerExample example = new AnswerExample();
        Criteria criteria = example.createCriteria();
        criteria.andStatusEqualTo(1);
        
        if(page.getParam().getProblemId()!=null){
            criteria.andProblemIdEqualTo(page.getParam().getProblemId());
        }
        
        example.setOrderByClause(order+" desc");
        
        //查询好友回答
        List<Integer> userlist=relationShipDao.findrelation(page.getParam().getUserId());
        userlist.add(page.getParam().getUserId());
        criteria.andUserIdIn(userlist);
        
        List<Answer> list=answerDao.selectBypage(example, page);
        
        List<AnswerResult> list1=new ArrayList<>();
        
        for(Answer answer:list){
            AnswerResult result=new AnswerResult();
            
            //查询用户昵称和头像
            IMUser user=userDao.selectByPrimaryKey(answer.getUserId());
            
            setResult(result,answer,user);
            
            list1.add(result);
        }
        
        Page<AnswerResult> reslut = new Page<AnswerResult>();
        
        reslut.setPageNo(page.getPageNo());
        reslut.setPageSize(page.getPageSize());
        reslut.setTotalCount(page.getTotalCount());
        reslut.setTotalPageCount(page.getTotalPageCount());
        reslut.setResult(list1);
       
        return res.success(reslut);
    }

    @Override
    public Response query(Integer answer_id,Integer user_id) {
        Response res = new Response();
        AnswerResult result=new AnswerResult();
        
        Answer answer =answerDao.selectByPrimaryKey(answer_id);
        
        //判断该问题是否已经点赞
        Integer i=0;       //i=0 表示未点赞    i=1  表示已点赞
        AgreeExample example = new AgreeExample();
        AgreeExample.Criteria criteria=example.createCriteria();
        criteria.andAnswerIdEqualTo(answer_id);
        criteria.andUserIdEqualTo(user_id);
        List<Agree> list=agreeDao.selectByExample(example);
        
        if(list.size()!=0){
            i=list.get(0).getStatus()==1?1:0;
        }else {
            i=0;
        }
                
        if(answer.getUserId()!=user_id){
            //查询用户昵称和头像
            IMUser user=userDao.selectByPrimaryKey(answer.getUserId());
            
            setResult(result,answer,user);
            result.setStatus(i);
            return res.success(result);
        }
        
        Jedis jedis = null;
        try {
            jedis=RedisManager.getInstance().getResource(Constants.REDIS_POOL_NAME_MEMBER);
            
            String key=answer.getId()+"agree";
            String value=answer.getAgreeNum()+"";
            jedis.set(key, value);
            
            /*jedis.setex(key, Constants.ACCESS_TOKEN_EXPIRE_SECONDS, value);*/
            
            key=answer.getId()+"comment";
            value=answer.getCommentNum()+"";
            jedis.set(key, value);
            
            /*jedis.setex(key, Constants.ACCESS_TOKEN_EXPIRE_SECONDS, value);*/
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            RedisManager.getInstance().returnResource(Constants.REDIS_POOL_NAME_MEMBER, jedis);
        }
        
        //查询用户昵称和头像
        IMUser user=userDao.selectByPrimaryKey(user_id);
        
        setResult(result,answer,user);
        result.setStatus(i);
        return res.success(result);
    }
    
    private void setResult(AnswerResult result,Answer answer,IMUser user){
        result.setId(answer.getId());
        result.setUserId(answer.getUserId());
        result.setProblemId(answer.getProblemId());
        result.setContent(answer.getContent());
        result.setAgreeNum(answer.getAgreeNum());
        result.setCommentNum(answer.getCommentNum());
        result.setStatus(answer.getStatus());
        result.setUpdated(answer.getUpdated());
        result.setCreated(answer.getCreated());
        result.setNick(user.getNick());
        result.setAvatar(user.getAvatar());
    }

    @Override
    public Response queryBack(Page<Answer> page) {
        Response resp = new Response();
        
        AnswerExample example = new AnswerExample();
        AnswerExample.Criteria criteria = example.createCriteria();
        example.setOrderByClause("created desc");
        
        if(page.getParam().getUserId()!=null){
            criteria.andUserIdEqualTo(page.getParam().getUserId());
        }
        if(page.getParam().getProblemId()!=null){
            criteria.andProblemIdEqualTo(page.getParam().getProblemId());
        }
        
        List<Answer> list=answerDao.selectBypage(example, page);
        page.setParam(null);
        page.setResult(list);
        resp.success(page);
        return resp;
    }

    @Override
    public Response update(Answer param) {
        Response resp = new Response();
        
        param.setUpdated(DateUtil.getLinuxTimeStamp());
        answerDao.updateByPrimaryKeySelective(param);
        
        resp.success();
        return resp;
    }

}
