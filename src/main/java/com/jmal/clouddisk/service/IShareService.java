package com.jmal.clouddisk.service;

import com.jmal.clouddisk.model.ShareDO;
import com.jmal.clouddisk.model.SharerDTO;
import com.jmal.clouddisk.model.UploadApiParamDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.util.ResponseResult;

import java.util.List;

/**
 * @Description IShareService
 * @Author jmal
 * @Date 2020-03-17 16:21
 */
public interface IShareService {

    /***
     * 生成分享链接
     * @param share
     * @return
     */
    ResponseResult<Object> generateLink(ShareDO share);

    /***
     * 访问分享链接
     * @param shareId
     * @param pageIndex
     * @param pageSize
     * @return
     */
    ResponseResult<Object> accessShare(String shareId, Integer pageIndex, Integer pageSize);

    /***
     * 获取分享信息
     * @param share
     * @return
     */
    ShareDO getShare(String share);

    /***
     * 检查是否过期
     * @param shareDO
     * @return
     */
    boolean checkWhetherExpired(ShareDO shareDO);

    /***
     * 检查是否过期
     * @param share
     * @return
     */
    boolean checkWhetherExpired(String share);

    /***
     * 打开目录
     * @param share
     * @param fileId
     * @param pageIndex
     * @param pageSize
     * @return
     */
    ResponseResult<Object> accessShareOpenDir(ShareDO share, String fileId, Integer pageIndex, Integer pageSize);

    /***
     * 获取分享列表
     * @param upload
     * @return
     */
    List<ShareDO> getShareList(UploadApiParamDTO upload);

    /***
     * 分享列表
     * @param upload
     * @return
     */
    ResponseResult<Object> shareList(UploadApiParamDTO upload);

    /***
     * 取消分享
     * @param shareIdList
     * @return
     */
    ResponseResult<Object> cancelShare(String[] shareIdList);

    /***
     * 删除关联分享
     * @param userList
     */
    void deleteAllByUser(List<ConsumerDO> userList);

    /***
     * 获取分享者信息
     * @param userId userId
     * @return ResponseResult
     */
    ResponseResult<SharerDTO> getSharer(String userId);
}
