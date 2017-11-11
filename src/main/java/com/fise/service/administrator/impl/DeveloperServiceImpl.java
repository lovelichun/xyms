package com.fise.service.administrator.impl;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fise.base.ErrorCode;
import com.fise.base.Page;
import com.fise.base.Response;
import com.fise.dao.WiAdminMapper;
import com.fise.model.entity.WiAdmin;
import com.fise.model.entity.WiAdminExample;
import com.fise.model.entity.WiAdminExample.Criteria;
import com.fise.model.param.DeveloperInsert;
import com.fise.model.param.DeveloperQuery;
import com.fise.model.param.DeveloperUpdate;
import com.fise.model.result.DeveloperResult;
import com.fise.service.administrator.IDeveloperService;
import com.fise.utils.Constants;
import com.fise.utils.DateUtil;
import com.fise.utils.StringUtil;

@Service
public class DeveloperServiceImpl implements IDeveloperService {
	Logger logger = Logger.getLogger(DeveloperServiceImpl.class);

	@Autowired
	private WiAdminMapper adminDao;

	@Override
	public Response insert(DeveloperInsert param,List<MultipartFile> uploadfile) {
		Response response = new Response();
		WiAdmin developer = new WiAdmin();
		
		WiAdminExample example = new WiAdminExample();
		Criteria con = example.createCriteria();
		con.andAccountEqualTo(param.getAccount());
		
		List<WiAdmin> queryAccount =adminDao.selectByExample(example);
		if(queryAccount.size()!=0){
			response.failure(ErrorCode.ERROR_ACCOUNT_ALREADY_EXISTED);
			response.setMsg("该账户已注册");
			return response;
		}
		developer.setAccount(param.getAccount());
		
		developer.setPassword(param.getPassword());
		developer.setNickName(param.getNickName());
		developer.setPhone(param.getPhone());
		developer.setEmail(param.getEmail());
		// 默认不可用
		developer.setStatus(0);
		developer.setCreated(DateUtil.getLinuxTimeStamp());
		developer.setUpdated(DateUtil.getLinuxTimeStamp());
		developer.setIdCard(param.getIdCard());
		// 三张身份证的照片，上传到服务器中，获取它们的地址值，用;隔开
		List<String> images = null;
		try {
			images = photoUpload(uploadfile);
		} catch (Exception e) {
			response.failure(ErrorCode.ERROR_PARAM_BIND_EXCEPTION);
			response.setMsg("上传图片失败");
			return response;
		}
	    String imagesUrl=StringUtil.combineStr(images);
		developer.setCardPhoto(param.getCardPhoto());
		developer.setDescription(param.getDescription());
		developer.setUserType(param.getUserType());
		developer.setCardPhoto(imagesUrl);

		adminDao.insert(developer);
		response.success(developer);
		return response;
	}

	private List<String> photoUpload(List<MultipartFile> uploadfile) throws IllegalStateException, IOException {
		MultipartFile file = null;
		String pictureURL = "";
		List<String> result = new ArrayList<String>();
		// 上传图片文件
		if (uploadfile.size() != 0) {
			for (int i = 0; i < uploadfile.size(); i++) {
				file = uploadfile.get(i);

				/* 内网上传图片路径 */
				String path = "/home/fise/bin/www/upload";
				/* 外网上传图片路径 */
				//String path="D:/logs";

				String filename = file.getOriginalFilename().replace(".",
						new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".");
				File dir = new File(path, filename);
				if (!dir.exists()) {
					dir.mkdirs();
				}
				file.transferTo(dir);
				Runtime.getRuntime().exec("chown fise:fise "+path+"/"+filename);
			    pictureURL = Constants.FILE_UPLOAD_URL + "/" + filename;
				result.add(pictureURL);
			}
		}
		return result;
	}

	@Override
	public Response update(DeveloperUpdate developer) {
		
		Response response = new Response();
		WiAdmin wiadmin=new WiAdmin();
		
		WiAdminExample example = new WiAdminExample();
		Criteria updWhere = example.createCriteria();
		updWhere.andIdEqualTo(developer.getAdminId());
		
		wiadmin.setStatus(developer.getStatus());
		wiadmin.setRoleId(31);
		wiadmin.setCompanyId(1);
		wiadmin.setUpdated(DateUtil.getLinuxTimeStamp());
		wiadmin.setRemarks(developer.getRemarks());
		int result=adminDao.updateByExampleSelective(wiadmin, example);
		if (result==0){
			response.setErrorCode(ErrorCode.ERROR_PARAM_BIND_EXCEPTION);
			response.setMsg("开发者审核失败");
			return response;
		}
		return response;
	}

	@Override
	public Response query(Page<DeveloperQuery> param) {
		Response response = new Response();
		WiAdminExample example = new WiAdminExample();
		Criteria con = example.createCriteria();
		con.andUserTypeNotEqualTo(0);
		if(param.getParam().getDevId()!=null){
			con.andIdEqualTo(param.getParam().getDevId());
		}
		if(StringUtil.isNotEmpty(param.getParam().getAccount())){
			con.andAccountEqualTo(param.getParam().getAccount());
		}
	
		if(param.getParam().getStatus()!=null){
			int status=param.getParam().getStatus();
			con.andStatusEqualTo((byte)status);
		}
		if(param.getParam().getUserType()!=null){
			con.andUserTypeEqualTo(param.getParam().getUserType());
		}
		
		List<WiAdmin> adminList =adminDao.selectByPage(example, param);
		if(adminList.size()==0){
			response.setErrorCode(ErrorCode.ERROR_SEARCH_UNEXIST);
			response.setMsg("开发者资源不足");
			return response;
		}
		List<DeveloperResult> list=new ArrayList<DeveloperResult>();
		for(int i=0;i<adminList.size();i++){
			DeveloperResult result=new DeveloperResult();
			result.init(adminList.get(i));
			list.add(result);
		}
		Page <DeveloperResult> page=new Page <DeveloperResult>();
		page.setPageNo(param.getPageNo());
		page.setPageSize(param.getPageSize());
		page.setTotalCount(param.getTotalCount());
		page.setTotalPageCount(param.getTotalPageCount());
		page.setResult(list);
        response.success(page);
        return response;
	}

	@Override
	public Response queryAccount(String account) {
		Response response = new Response();
		WiAdminExample example = new WiAdminExample();
		Criteria con = example.createCriteria();
		con.andAccountEqualTo(account);
		
		List<WiAdmin> queryAccount =adminDao.selectByExample(example);
		if(queryAccount.size()!=0){
			response.failure(ErrorCode.ERROR_ACCOUNT_ALREADY_EXISTED);
			response.setMsg("该账户已注册");
			return response;
		}
		response.setCode(200);
		response.setMsg("该账户未注册");
		return response;
	}

	@Override
	public Response delete(Integer id) {
		Response response = new Response();
		WiAdmin wiadmin=new WiAdmin();
		WiAdmin queryDev=  adminDao.selectByPrimaryKey(id);
		if(queryDev==null){
			response.setErrorCode(ErrorCode.ERROR_MEMBER_INDB_IS_NULL);
			response.setMsg("开发者已不存在");
			return response;
		}
		
		WiAdminExample example = new WiAdminExample();
		Criteria updWhere = example.createCriteria();
		updWhere.andIdEqualTo(id);
		wiadmin.setStatus(4);
		int result=adminDao.updateByExampleSelective(wiadmin, example);
		if (result==0){
			response.setErrorCode(ErrorCode.ERROR_PARAM_BIND_EXCEPTION);
			response.setMsg("开发者删除失败");
			return response;
		}
		return response;
	}

}
