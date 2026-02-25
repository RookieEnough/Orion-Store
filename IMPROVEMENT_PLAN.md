# Orion Store Geliştirme Planı

## 🚨 Acil (Critical - Phase 0)

### 0.1 localData.ts Eksik Dosya Sorunu ✅ ÇÖZÜLDÜ
- **Sorun**: `import { localAppsData } from './localData'` - dosya mevcut değildi
- **Etki**: Uygulama hiç çalışmıyordu
- **Çözüm**: `localData.ts` dosyası oluşturuldu (5 örnek uygulama ile)
- **Tarih**: 2026-02-25

---

## 📋 Phase 1: Mimari Yeniden Yapılandırma ✅ DEVAM EDİYOR

### 1.1 Custom Hooks Oluştur ✅ TAMAMLANDI
```
src/hooks/
├── useTheme.ts          ✅ Tema yönetimi
├── useApps.ts           ✅ Uygulama verisi yönetimi
└── index.ts            ✅ Export dosyası
```

### 1.2 Zustand Store Entegrasyonu ✅ TAMAMLANDI
```
src/stores/
├── appStore.ts          ✅ Uygulama state'i
├── settingsStore.ts     ✅ Kullanıcı ayarları
├── downloadStore.ts     ✅ İndirme durumu
└── index.ts            ✅ Export dosyası
```

### 1.3 Error Boundary ✅ TAMAMLANDI
- `components/ErrorBoundary.tsx` oluşturuldu
- Global hata yakalama mekanizması eklendi

---

## 🔐 Phase 2: Güvenlik İyileştirmeleri

### 2.1 GitHub Token Şifreleme
- [ ] Web Crypto API kullanarak token encryption
- [ ] sessionStorage'a taşıma opsiyonu

### 2.2 URL Validation
- [ ] Enhanced sanitizeUrl fonksiyonu
- [ ] URL whitelist mekanizması

### 2.3 Content Security Policy
- [ ] CSP header ekleme
- [ ] Script injection koruması

---

## 🧪 Phase 3: Test ve Hata Yönetimi

### 3.1 Error Boundaries ✅ TAMAMLANDI
- [x] Global error boundary
- [ ] Component-level error boundaries

### 3.2 Test Altyapısı
- [ ] Jest + React Testing Library konfigürasyonu
- [ ] Cypress e2e testleri (zaten yüklü)

---

## ⚡ Phase 4: Performans

### 4.1 Virtual Scrolling
- [ ] react-window entegrasyonu (zaten yüklü!)

### 4.2 Lazy Loading ✅ MEVCUT
- [x] Component lazy loading (zaten var)
- [ ] Image lazy loading optimizasyonu

### 4.3 Service Worker
- [ ] Offline desteği
- [ ] PWA manifest güncelleme

---

## 🎨 Phase 5: UX İyileştirmeleri

### 5.1 Accessibility (a11y)
- [ ] ARIA labels ekleme
- [ ] Keyboard navigation
- [ ] Screen reader desteği

### 5.2 Animations
- [ ] Reduced motion desteği
- [ ] Performance-optimized transitions

---

## 📅 Öncelik Sırası (Güncellendi)

| Öncelik | Görev | Durum | Tahmini Süre |
|----------|-------|-------|--------------|
| 1 | localData.ts fix | ✅ Tamamlandı | 30 dk |
| 2 | Zustand stores | ✅ Tamamlandı | 2 saat |
| 3 | Custom hooks | ✅ Tamamlandı | 2 saat |
| 4 | Error boundary | ✅ Tamamlandı | 30 dk |
| 5 | Security fixes | ⏳ Planlanıyor | 1 saat |
| 6 | App.tsx refactor | ⏳ Planlanıyor | 4 saat |
| 7 | Testing setup | ⏳ Planlanıyor | 4 saat |
| 8 | Performance | ⏳ Planlanıyor | 2 saat |

---

## 🎯 Başarı Kriterleri

- [x] Uygulama hatasız çalışıyor (localData.ts eklendi)
- [ ] TypeScript errors = 0
- [ ] Lighthouse performans > 90
- [ ] Test coverage > 70%
- [ ] a11y score > 80

---

## 📝 Yapılan Değişiklikler

### 2026-02-25
1. **localData.ts** - Örnek uygulama verileri eklendi
2. **stores/appStore.ts** - Zustand uygulama store oluşturuldu
3. **stores/settingsStore.ts** - Zustand ayarlar store oluşturuldu
4. **stores/downloadStore.ts** - Zustand indirme store oluşturuldu
5. **hooks/useTheme.ts** - Tema yönetimi hook'u oluşturuldu
6. **hooks/useApps.ts** - Uygulama verisi hook'u oluşturuldu
7. **components/ErrorBoundary.tsx** - Hata yönetimi bileşeni eklendi
