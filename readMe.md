# MicEngine(Motor Sesine Göre Ses Çıkartan Uygulama)

Bu uygulama, mikrofon aracılığıyla alınan ses şiddetine göre, hoparlörden motor sesi duymanızı sağlar. Mikrofonla alınan sesin şiddetine göre telefonun höpörlöründen anlık olarak motor sesi yüksekliği artacaktır, Ses çok artarsa egzoz patlama sesleri vs çesitli efetler gelecektir.

### Detaylı Açıklama:
Bu uygulama, Android üzerinde çalışacak şekilde tasarlanmıştır. Temel olarak mikrofon verisini alır ve ses şiddetine göre motor sesinin hızını ve ses seviyesini dinamik olarak ayarlar. Kullanıcılar, ses şiddeti arttıkça motor sesinin daha hızlı ve güçlü çaldığını duyacaklardır(Araba Motor sesi). Aşağıda her özelliğin nasıl çalışacağına dair detaylar verilmiştir:

2. **Mikrofon Erişimi Sağlama**: Uygulama, mikrofon erişimi izni almak için Android manifest dosyasına gerekli izinleri ekleyecek ve çalışırken kullanıcıdan izin alacaktır.
3. **Mikrofon Verisini Alma**: `AudioRecord` sınıfı kullanılarak mikrofon verisi alınacak ve bu veriler her 100ms’de bir örneklenecektir.
4. **Ses Şiddetini Hesaplama (RMS)**: Mikrofon verisinden ses şiddeti hesaplanacak ve bu veri, motor sesinin hızını ve ses seviyesini değiştirmek için kullanılacaktır.
5. **Motor Sesini Oynatma**: `MediaPlayer` sınıfı, motor sesinin oynatılmasını sağlayacaktır.
6. **Sesin Hızını ve Volume'unu Dinamik Olarak Ayarlama**: Mikrofon verisinden alınan RMS değeri ile motor sesinin hızını ve ses seviyesini dinamik olarak ayarlayan fonksiyonlar yazılacaktır.
7. **Ses Geçişlerini Yumuşatma**: Sesin aniden değişmemesi için geçişler yumuşatılacak ve `Handler` sınıfı ile geçişler kontrol edilecektir.
8. **UI Tasarımı ve Göstergeler**: Kullanıcıya sesin şiddetini görsel olarak gösteren bir UI öğesi (örneğin progress bar) eklenip, ses seviyesinin arttıkça UI da dinamik olarak değişecektir.

Proje Android platformunda çalışacak ve kullanıcıya gerçek zamanlı motor sesi deneyimi sunacaktır.