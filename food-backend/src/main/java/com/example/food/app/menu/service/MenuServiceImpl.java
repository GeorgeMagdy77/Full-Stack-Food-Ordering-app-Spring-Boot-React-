package com.example.food.app.menu.service;

import com.example.food.app.category.entity.Category;
import com.example.food.app.category.repository.CategoryRepository;
import com.example.food.app.exceptions.BadRequestException;
import com.example.food.app.exceptions.NotFoundException;
import com.example.food.app.local_storage.LocalFileStorageService;
import com.example.food.app.menu.dto.MenuDTO;
import com.example.food.app.menu.entity.Menu;
import com.example.food.app.menu.repository.MenuRepository;
import com.example.food.app.response.Response;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MenuServiceImpl implements MenuService {

    private final MenuRepository menuRepository;
    private final CategoryRepository categoryRepository;
    private final ModelMapper modelMapper;
    private final LocalFileStorageService localFileStorageService; // بدل AWSS3Service

    @Override
    public Response<MenuDTO> createMenu(MenuDTO menuDTO) {
        log.info("Inside createMenu()");

        Category category = categoryRepository.findById(menuDTO.getCategoryId())
                .orElseThrow(() -> new NotFoundException("Category not found with ID: " + menuDTO.getCategoryId()));

        MultipartFile imageFile = menuDTO.getImageFile();
        if (imageFile == null || imageFile.isEmpty()) {
            throw new BadRequestException("Menu Image is needed");
        }

        // رفع الصورة محليًا (بنفس الاسم – مع prefix/رقم لو فيه تعارض) ثم تحويل URL إلى String
        //URL url = localFileStorageService.uploadFile("menus", imageFile, currentUserEmail());
        URL url = localFileStorageService.storeMenuImage(imageFile, menuDTO.getName(), menuDTO.getCategoryId());
        String imageUrl = url.toString();

        Menu menu = Menu.builder()
                .name(menuDTO.getName())
                .description(menuDTO.getDescription())
                .price(menuDTO.getPrice())
                .imageUrl(imageUrl)
                .category(category)
                .build();

        Menu savedMenu = menuRepository.save(menu);

        return Response.<MenuDTO>builder()
                .statusCode(HttpStatus.OK.value())
                .message("Menu created successfully")
                .data(modelMapper.map(savedMenu, MenuDTO.class))
                .build();
    }

    @Override
    @Transactional // ✅ إضافة: لضمان rollback لو حصل Exception
    public Response<MenuDTO> updateMenu(MenuDTO menuDTO) {
        log.info("Inside updateMenu()");

        Menu existingMenu = menuRepository.findById(menuDTO.getId())
                .orElseThrow(() -> new NotFoundException("Menu not found "));

        Category category = categoryRepository.findById(menuDTO.getCategoryId())
                .orElseThrow(() -> new NotFoundException("Category not found "));

        String finalImageUrl = existingMenu.getImageUrl();
        MultipartFile imageFile = menuDTO.getImageFile();



        // لو فيه صورة جديدة: امسح القديمة (محليًا) وارفع الجديدة
        if (imageFile != null && !imageFile.isEmpty()) {
//            if (imageUrl != null && !imageUrl.isEmpty()) {
//                localFileStorageService.deleteFile(imageUrl);
//                log.info("Deleted old menu image from local storage");
//            }
//            URL newUrl = localFileStorageService.uploadFile("menus", imageFile, currentUserEmail());
//            imageUrl = newUrl.toString();
//              URL url = localFileStorageService.storeMenuImage(imageFile, menuDTO.getName(), menuDTO.getCategoryId());
//              existingMenu.setImageUrl(url.toString());



//            URL newUrl = localFileStorageService.storeMenuImage(
//                    imageFile,
//                    menuDTO.getName() != null ? menuDTO.getName() : existingMenu.getName(),
//                    menuDTO.getCategoryId() != null ? menuDTO.getCategoryId() : existingMenu.getCategory().getId()
//            );
//            String oldUrl = existingMenu.getImageUrl();
//            finalImageUrl = newUrl.toString();
//
//            if (oldUrl != null && !oldUrl.isBlank()) {
//                try {
//                    localFileStorageService.deleteFile(oldUrl);
//                    log.info("Deleted old menu image from local storage");
//                } catch (Exception ex) {
//                    log.warn("Failed deleting old image '{}': {}", oldUrl, ex.getMessage());
//                }
//            }


            String oldUrl = existingMenu.getImageUrl();

            // ✅ الكود الجديد: حذف أولاً ثم رفع
            if (oldUrl != null && !oldUrl.isBlank()) {
                try {
                    localFileStorageService.deleteFile(oldUrl); // 🗑️ حذف القديمة أولاً
                    log.info("Deleted old menu image from local storage");
                } catch (Exception ex) {
                    log.warn("Failed deleting old image '{}': {}", oldUrl, ex.getMessage());
                    // ملاحظة: بناءً على طلبك هنكمّل ونحاول نرفع الجديدة حتى لو الحذف فشل
                }
            }

            try {
                URL newUrl = localFileStorageService.storeMenuImage(
                        imageFile,
                        menuDTO.getName() != null ? menuDTO.getName() : existingMenu.getName(),
                        menuDTO.getCategoryId() != null ? menuDTO.getCategoryId() : existingMenu.getCategory().getId()
                );
                finalImageUrl = newUrl.toString(); // ✨ حفظ رابط الجديدة
            } catch (Exception uploadEx) {
                // ⚠️ اختيارياً: ممكن ترمي استثناء لو الرفع فشل لأن القديمة خلاص اتمسحت
                log.error("Uploading new image failed after deleting old one: {}", uploadEx.getMessage(), uploadEx);
                throw uploadEx; // أو تقدر ترجعها Response مخصص حسب سياستك
            }
        }

        if (menuDTO.getName() != null && !menuDTO.getName().isBlank()) {
            existingMenu.setName(menuDTO.getName());
        }
        if (menuDTO.getDescription() != null && !menuDTO.getDescription().isBlank()) {
            existingMenu.setDescription(menuDTO.getDescription());
        }
        if (menuDTO.getPrice() != null) {
            existingMenu.setPrice(menuDTO.getPrice());
        }

        //existingMenu.setImageUrl(imageUrl);
        existingMenu.setImageUrl(finalImageUrl);
        existingMenu.setCategory(category);

        Menu updatedMenu = menuRepository.save(existingMenu);

        return Response.<MenuDTO>builder()
                .statusCode(HttpStatus.OK.value())
                .message("Menu updated successfully")
                .data(modelMapper.map(updatedMenu, MenuDTO.class))
                .build();
    }

    @Override
    public Response<MenuDTO> getMenuById(Long id) {
        log.info("Inside getMenuById()");

        Menu menu = menuRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Menu not found"));

        MenuDTO menuDTO = modelMapper.map(menu, MenuDTO.class);

        // لو عندك Reviews وحابب ترتيبها، رجّع السطر المناسب هنا

        return Response.<MenuDTO>builder()
                .statusCode(HttpStatus.OK.value())
                .message("Menu retrieved successfully")
                .data(menuDTO)
                .build();
    }

    @Override
    public Response<?> deleteMenu(Long id) {
        log.info("Inside deleteMenu()");

        Menu menuToDelete = menuRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Menu not found with ID: " + id));

        // امسح الصورة من التخزين المحلي (سواء أرسلت URL كامل أو مفتاح نسبي)
        String imageUrl = menuToDelete.getImageUrl();
        if (imageUrl != null && !imageUrl.isEmpty()) {
            localFileStorageService.deleteFile(imageUrl);
            log.info("Deleted image from local storage: {}", imageUrl);
        }

        menuRepository.deleteById(id);
        return Response.builder()
                .statusCode(HttpStatus.OK.value())
                .message("Menu deleted successfully")
                .build();
    }

    @Override
    public Response<List<MenuDTO>> getMenus(Long categoryId, String search) {
        log.info("Inside getMenus()");

        Specification<Menu> spec = buildSpecification(categoryId, search);
        Sort sort = Sort.by(Sort.Direction.DESC, "id");

        List<Menu> menuList = menuRepository.findAll(spec, sort);

        List<MenuDTO> menuDTOS = menuList.stream()
                .map(menu -> modelMapper.map(menu, MenuDTO.class))
                .toList();

        return Response.<List<MenuDTO>>builder()
                .statusCode(HttpStatus.OK.value())
                .message("Menus retrieved")
                .data(menuDTOS)
                .build();
    }

    private Specification<Menu> buildSpecification(Long categoryId, String search) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }

            if (search != null && !search.isBlank()) {
                String term = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), term),
                        cb.like(cb.lower(root.get("description")), term)
                ));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String currentUserEmail() {
        // رجّع الإيميل الحقيقي لو عندك UserDetails مخصص
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getName() != null ? auth.getName() : "user@example.com";
    }
}
