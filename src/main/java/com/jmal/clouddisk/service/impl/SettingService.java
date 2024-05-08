package com.jmal.clouddisk.service.impl;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.PathUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.crypto.symmetric.SymmetricAlgorithm;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.repository.IAuthDAO;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.util.MongoUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author jmal
 * @Description 设置
 * @Date 2020/10/28 5:30 下午
 */
@Service
@Slf4j
public class SettingService {

    @Autowired
    FileProperties fileProperties;

    @Autowired
    CommonFileService commonFileService;

    @Autowired
    private MongoTemplate mongoTemplate;

    protected static final String COLLECTION_NAME_WEBSITE_SETTING = "websiteSetting";

    @Autowired
    private IAuthDAO authDAO;

    @Autowired
    private MenuService menuService;

    @Autowired
    private UserServiceImpl userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    UserLoginHolder userLoginHolder;

    @Autowired
    LuceneService luceneService;

    private static final Map<String, SyncFileVisitor> syncFileVisitorMap = new ConcurrentHashMap<>(16);

    private static final Map<String, String> syncCache = new ConcurrentHashMap<>(16);

    private static final String SYNCED = "synced";

    @PostConstruct
    public void init() {
        // 启动时检测是否存在菜单，不存在则初始化
        if (!menuService.existsMenu()) {
            log.info("初始化角色、菜单！");
            menuService.initMenus();
            roleService.initRoles();
        }
        // 启动时检测是否存在lucene索引，不存在则初始化
        if (!luceneService.checkIndexExists()) {
            List<String> usernames = userService.getAllUsernameList();
            usernames.forEach(this::sync);
        }
    }

    /***
     * 把文件同步到数据库
     * @param username 用户名
     */
    public ResponseResult<Object> sync(String username) {
        syncCache.computeIfAbsent(username, key -> {
            ThreadUtil.execute(() -> {
                Path path = Paths.get(fileProperties.getRootDir(), username);
                TimeInterval timeInterval = new TimeInterval();
                try {

                    Set<FileVisitOption> fileVisitOptions = EnumSet.noneOf(FileVisitOption.class);
                    fileVisitOptions.add(FileVisitOption.FOLLOW_LINKS);

                    // 先删除索引
                    luceneService.deleteAllIndex(userService.getUserIdByUserName(username));
                    FileCountVisitor fileCountVisitor = new FileCountVisitor();
                    Files.walkFileTree(path, fileVisitOptions, Integer.MAX_VALUE, fileCountVisitor);
                    log.info("user: {}, 开始同步, 文件数: {}", username, fileCountVisitor.getCount());
                    timeInterval.start();
                    SyncFileVisitor syncFileVisitor = new SyncFileVisitor(username, fileCountVisitor.getCount());
                    syncFileVisitorMap.put(username, syncFileVisitor);
                    Files.walkFileTree(path, fileVisitOptions, Integer.MAX_VALUE, syncFileVisitor);
                    TimeUnit.MINUTES.sleep(1);
                    // 删除有删除标记的doc
                    //commonFileService.deleteDocByDeleteFlag(username);
                } catch (IOException e) {
                    log.error("{}{}", e.getMessage(), path, e);
                } catch (InterruptedException e) {
                    log.error(e.getMessage(), e);
                } finally {
                    syncCache.remove(username);
                    syncFileVisitorMap.remove(username);
                    log.info("user: {}, 同步完成, 耗时: {}s", username, timeInterval.intervalSecond());
                    commonFileService.pushMessage(username, 100, SYNCED);
                }
            });
            return "syncing";
        });
        return ResultUtil.success();
    }

    /**
     * 是否正在同步中
     */
    public ResponseResult<Object> isSync(String username) {
        if (!syncCache.containsKey(username)) {
            return ResultUtil.success(100);
        }
        if (syncFileVisitorMap.containsKey(username)) {
            return ResultUtil.success(syncFileVisitorMap.get(username).getPercent());
        }
        return ResultUtil.success(100);
    }

    /**
     * 上传网盘logo
     *
     * @param file logo文件
     */
    public ResponseResult<Object> uploadLogo(MultipartFile file) {
        String filename = "logo-" + System.currentTimeMillis() + "." + FileUtil.extName(file.getOriginalFilename());
        File dist = new File(fileProperties.getRootDir() + File.separator + filename);
        try {
            String oldFilename = null;
            Query query = new Query();
            WebsiteSettingDO websiteSettingDO = mongoTemplate.findOne(query, WebsiteSettingDO.class, COLLECTION_NAME_WEBSITE_SETTING);
            if (websiteSettingDO != null) {
                oldFilename = websiteSettingDO.getNetdiskLogo();
            }
            // 保存新的logo文件
            FileUtil.writeFromStream(file.getInputStream(), dist);
            Update update = new Update();
            update.set("netdiskLogo", filename);
            mongoTemplate.upsert(new Query(), update, COLLECTION_NAME_WEBSITE_SETTING);
            if (!CharSequenceUtil.isBlank(oldFilename)) {
                // 删除之前的logo文件
                PathUtil.del(Paths.get(fileProperties.getRootDir(), oldFilename));
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return ResultUtil.error("上传网盘logo失败");
        }
        return ResultUtil.success(filename);
    }

    /**
     * 修改网盘名称
     *
     * @param netdiskName 网盘名称
     */
    public ResponseResult<Object> updateNetdiskName(String netdiskName) {
        Query query = new Query();
        Update update = new Update();
        update.set("netdiskName", netdiskName);
        mongoTemplate.upsert(query, update, COLLECTION_NAME_WEBSITE_SETTING);
        return ResultUtil.success("修改成功");
    }

    private class SyncFileVisitor extends SimpleFileVisitor<Path> {

        private final String username;
        private final double totalCount;

        @Getter
        private int percent = 0;

        private final AtomicLong processCount;

        public SyncFileVisitor(String username, double totalCount) {
            this.username = username;
            this.totalCount = totalCount;
            this.processCount = new AtomicLong(0);
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            log.error(exc.getMessage(), exc);
            return super.visitFileFailed(file, exc);
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            createFile(dir);
            return super.preVisitDirectory(dir, attrs);
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            createFile(file);
            return super.visitFile(file, attrs);
        }

        private void createFile(Path file) {
            try {
                commonFileService.createFile(username, file.toFile(), null, null);
            } catch (Exception e) {
                log.error("{}{}", e.getMessage(), file, e);
                FileDocument fileDocument = commonFileService.getFileDocument(username, file.toFile().getAbsolutePath());
                if (fileDocument != null) {
                    // 需要移除删除标记
                    removeDeleteFlagOfDoc(fileDocument.getId());
                }
            } finally {
                if (totalCount > 0) {
                    if (processCount.get() <= 2) {
                        commonFileService.pushMessage(username, 1, SYNCED);
                    }
                    processCount.addAndGet(1);
                    int currentPercent = (int) (processCount.get()/totalCount * 100);
                    if (currentPercent > percent) {
                        commonFileService.pushMessage(username, currentPercent, SYNCED);
                    }
                    percent = currentPercent;
                }
            }
        }
    }

    /**
     * 移除删除标记
     * @param fileId fileId
     */
    public void removeDeleteFlagOfDoc(String fileId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(fileId));
        Update update = new Update();
        update.unset("delete");
        mongoTemplate.updateMulti(query, update, CommonFileService.COLLECTION_NAME);
    }

    private static class FileCountVisitor extends SimpleFileVisitor<Path> {
        private final AtomicLong count = new AtomicLong(0);

        public long getCount() {
            return count.get();
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            count.addAndGet(1);
            return super.preVisitDirectory(dir, attrs);
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            count.addAndGet(1);
            return super.visitFile(file, attrs);
        }
    }

    /***
     * 更新网站设置
     * @param websiteSettingDO WebsiteSetting
     * @return ResponseResult
     */
    public ResponseResult<Object> websiteUpdate(WebsiteSettingDO websiteSettingDO) {
        Query query = new Query();
        Update update = MongoUtil.getUpdate(websiteSettingDO);
        // 添加心语记录
        addHeartwings(websiteSettingDO);
        mongoTemplate.upsert(query, update, COLLECTION_NAME_WEBSITE_SETTING);
        return ResultUtil.success();
    }

    /***
     * 添加心语记录
     * @param websiteSettingDO WebsiteSettingDO
     */
    private void addHeartwings(WebsiteSettingDO websiteSettingDO) {
        WebsiteSettingDO websiteSettingDO1 = mongoTemplate.findOne(new Query(), WebsiteSettingDO.class, COLLECTION_NAME_WEBSITE_SETTING);
        if (websiteSettingDO1 != null) {
            String oldHeartwings = websiteSettingDO1.getBackgroundTextSite();
            String heartwings = websiteSettingDO.getBackgroundTextSite();
            if (!CharSequenceUtil.isBlank(oldHeartwings) && !oldHeartwings.equals(heartwings)) {
                HeartwingsDO heartwingsDO = new HeartwingsDO();
                heartwingsDO.setCreateTime(LocalDateTimeUtil.now());
                heartwingsDO.setCreator(userLoginHolder.getUserId());
                heartwingsDO.setUsername(userLoginHolder.getUsername());
                heartwingsDO.setHeartwings(heartwings);
                mongoTemplate.save(heartwingsDO);
            }
        }
    }

    /**
     * 获取网站备案信息
     *
     * @return WebsiteSettingDTO
     */
    public WebsiteSettingDTO getWebsiteRecord() {
        WebsiteSettingDTO websiteSettingDTO = getWebsiteSetting();
        WebsiteSettingDTO websiteSettingDTO1 = new WebsiteSettingDTO();
        websiteSettingDTO1.setCopyright(websiteSettingDTO.getCopyright());
        websiteSettingDTO1.setRecordPermissionNum(websiteSettingDTO.getRecordPermissionNum());
        websiteSettingDTO1.setNetworkRecordNumber(websiteSettingDTO.getNetworkRecordNumber());
        websiteSettingDTO1.setNetworkRecordNumberStr(websiteSettingDTO.getNetworkRecordNumberStr());
        websiteSettingDTO1.setNetdiskName(websiteSettingDTO.getNetdiskName());
        websiteSettingDTO1.setNetdiskLogo(websiteSettingDTO.getNetdiskLogo());
        return websiteSettingDTO1;
    }

    /***
     * 获取网站设置
     * @return ResponseResult
     */
    public WebsiteSettingDTO getWebsiteSetting() {
        WebsiteSettingDTO websiteSettingDTO = new WebsiteSettingDTO();
        Query query = new Query();
        WebsiteSettingDO websiteSettingDO = mongoTemplate.findOne(query, WebsiteSettingDO.class, COLLECTION_NAME_WEBSITE_SETTING);
        if (websiteSettingDO != null) {
            BeanUtils.copyProperties(websiteSettingDO, websiteSettingDTO);
        }
        if (websiteSettingDTO.getAlonePages() == null) {
            websiteSettingDTO.setAlonePages(new ArrayList<>());
        }
        String avatar = userService.getCreatorAvatar();
        if (!CharSequenceUtil.isBlank(avatar)) {
            websiteSettingDTO.setAvatar(avatar);
        }
        return websiteSettingDTO;
    }

    public ResponseResult<List<HeartwingsDO>> getWebsiteHeartwings(Integer page, Integer pageSize, String order) {
        Query query = new Query();
        long count = mongoTemplate.count(query, HeartwingsDO.class);
        query.skip((long) pageSize * (page - 1));
        query.limit(pageSize);
        Sort.Direction direction = Sort.Direction.ASC;
        if ("descending".equals(order)) {
            direction = Sort.Direction.DESC;
        }
        query.with(Sort.by(direction, Constants.CREATE_TIME));
        return ResultUtil.success(mongoTemplate.find(query, HeartwingsDO.class)).setCount(count);
    }

    /***
     * 生成accessToken
     * @param username 用户名
     * @param tokenName tokenName
     * @return ResponseResult
     */
    public ResponseResult<String> generateAccessToken(String username, String tokenName) {
        byte[] key = SecureUtil.generateKey(SymmetricAlgorithm.AES.getValue()).getEncoded();
        // 构建
        AES aes = SecureUtil.aes(key);
        // 加密为16进制表示
        String accessToken = aes.encryptHex(username);
        UserAccessTokenDO userAccessTokenDO = new UserAccessTokenDO();
        userAccessTokenDO.setName(tokenName);
        userAccessTokenDO.setUsername(username);
        userAccessTokenDO.setAccessToken(accessToken);
        authDAO.generateAccessToken(userAccessTokenDO);
        return ResultUtil.success(accessToken);
    }

    /***
     * accessToken列表
     * @param username 用户名
     * @return List<UserAccessTokenDTO>
     */
    public ResponseResult<List<UserAccessTokenDTO>> accessTokenList(String username) {
        List<UserAccessTokenDTO> list = authDAO.accessTokenList(username);
        return ResultUtil.success(list);
    }

    /***
     * 删除accessToken
     * @param id accessTokenId
     */
    public void deleteAccessToken(String id) {
        authDAO.deleteAccessToken(id);
    }

    public void resetMenuAndRole() {
        menuService.initMenus();
        roleService.initRoles();
    }

}
