package com.jmal.clouddisk.controller;

import cn.hutool.extra.cglib.CglibUtil;
import com.jmal.clouddisk.model.Category;
import com.jmal.clouddisk.model.CategoryDTO;
import com.jmal.clouddisk.service.impl.CategoryService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author jmal
 * @Description 分类管理
 * @Date 2020/10/26 5:47 下午
 */
@Controller
@Api(tags = "分类管理")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    @ApiOperation("分类列表")
    @GetMapping("/category/list")
    @ResponseBody
    public ResponseResult<List<CategoryDTO>> list(@RequestParam String userId) {
        return ResultUtil.success(categoryService.list(userId));
    }

    @ApiOperation("分类树")
    @GetMapping("/category/tree")
    @ResponseBody
    public ResponseResult<List<Map<String, Object>>> tree(@RequestParam String userId) {
        return ResultUtil.success(categoryService.tree(userId));
    }

    @ApiOperation("分类信息")
    @GetMapping("/category/info")
    @ResponseBody
    public ResponseResult<CategoryDTO> categoryInfo(@RequestParam String userId, @RequestParam String categoryName) {
        CategoryDTO categoryDTO = new CategoryDTO();
        Category category = categoryService.getCategoryInfo(userId, categoryName);
        if(category != null){
            CglibUtil.copy(category, categoryDTO);
        }
        return ResultUtil.success(categoryDTO);
    }

    @ApiOperation("添加分类")
    @PostMapping("/category/add")
    @ResponseBody
    public ResponseResult<Object> add(@ModelAttribute @Validated CategoryDTO categoryDTO) {
        return categoryService.add(categoryDTO);
    }

    @ApiOperation("更新分类")
    @PutMapping("/category/update")
    @ResponseBody
    public ResponseResult<Object> update(@ModelAttribute CategoryDTO categoryDTO, @RequestParam String categoryId) {
        categoryDTO.setId(categoryId);
        return categoryService.update(categoryDTO);
    }

    @ApiOperation("删除分类")
    @DeleteMapping("/category/delete")
    @ResponseBody
    public ResponseResult<Object> delete(@RequestParam String userId, @RequestParam String[] categoryIds) {
        List<String> list = Arrays.asList(categoryIds);
        categoryService.delete(userId, list);
        return ResultUtil.success();
    }
}